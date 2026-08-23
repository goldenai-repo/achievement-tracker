import ComposeApp
import CoreLocation
import MapLibre
import UIKit

/// MapLibre map surface mirroring Android `VectorMap.android.kt` behavior.
final class AchievementMapView: UIView, MLNMapViewDelegate, UIGestureRecognizerDelegate {
    private let mapView: MLNMapView
    private var loadedStyleUrl: String?
    private var appliedCameraKey: String?
    private var boundaryLayerIds = Set<String>()
    private var pendingRender: (() -> Void)?
    private var suppressViewportCallback = false
    var listener: AchievementMapListener?

    override init(frame: CGRect) {
        mapView = MLNMapView(frame: frame)
        super.init(frame: frame)
        mapView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
        mapView.delegate = self
        addSubview(mapView)

        let tap = UITapGestureRecognizer(target: self, action: #selector(handleMapTap(_:)))
        tap.numberOfTapsRequired = 1
        tap.delegate = self
        mapView.addGestureRecognizer(tap)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }

    func bind(
        styleUrl: String,
        pointsJson: String,
        boundariesJson: String,
        viewportJson: String?,
        cameraResetKey: Int64
    ) {
        let render = { [weak self] in
            guard let self else { return }
            self.renderMarkers(pointsJson: pointsJson)
            self.renderBoundaries(boundariesJson: boundariesJson)
            let cameraKey = Self.cameraKey(viewportJson: viewportJson, cameraResetKey: cameraResetKey)
            if self.appliedCameraKey != cameraKey {
                self.appliedCameraKey = cameraKey
                self.applyViewport(viewportJson: viewportJson)
            }
        }

        if loadedStyleUrl != styleUrl {
            loadedStyleUrl = styleUrl
            appliedCameraKey = nil
            if let url = URL(string: styleUrl) {
                mapView.styleURL = url
            }
            pendingRender = render
        } else if mapView.style != nil {
            render()
        } else {
            pendingRender = render
        }
    }

    func mapView(_ mapView: MLNMapView, didFinishLoading style: MLNStyle) {
        pendingRender?()
        pendingRender = nil
    }

    func mapView(_ mapView: MLNMapView, imageFor annotation: MLNAnnotation) -> MLNAnnotationImage? {
        guard let point = annotation as? AchievementPointAnnotation else { return nil }
        let reuseId = point.isSearchSelection ? "search-selection-pin" : "checked-in-pin"
        if let existing = mapView.dequeueReusableAnnotationImage(withIdentifier: reuseId) {
            return existing
        }
        let color: UIColor = point.isSearchSelection
            ? UIColor(red: 234 / 255, green: 88 / 255, blue: 12 / 255, alpha: 1)
            : UIColor(red: 37 / 255, green: 99 / 255, blue: 235 / 255, alpha: 1)
        let image = Self.makePinImage(color: color)
        // Match Android marker anchoring: pin tip sits on the coordinate.
        let annotationImage = MLNAnnotationImage(image: image, reuseIdentifier: reuseId)
        annotationImage.centerOffset = CGVector(dx: 0, dy: -image.size.height / 2)
        return annotationImage
    }

    func mapView(_ mapView: MLNMapView, annotationCanShowCallout annotation: MLNAnnotation) -> Bool {
        // Android consumes marker clicks without showing an info window.
        false
    }

    func mapView(_ mapView: MLNMapView, didSelect annotation: MLNAnnotation) {
        mapView.deselectAnnotation(annotation, animated: false)
        guard let point = annotation as? AchievementPointAnnotation else { return }
        listener?.onPointClick(pointId: point.pointId)
    }

    /// Matches Android `OnCameraIdleListener`: report viewport only when idle.
    func mapViewDidBecomeIdle(_ mapView: MLNMapView) {
        guard !suppressViewportCallback else { return }
        emitViewportChanged()
    }

    @objc private func handleMapTap(_ gesture: UITapGestureRecognizer) {
        guard gesture.state == .ended else { return }
        let point = gesture.location(in: mapView)

        // Prefer marker selection; annotation taps are handled in didSelect.
        if let annotations = mapView.annotations {
            for annotation in annotations {
                let screen = mapView.convert(annotation.coordinate, toPointTo: mapView)
                let dx = screen.x - point.x
                let dy = screen.y - point.y
                if (dx * dx + dy * dy).squareRoot() < 36 {
                    return
                }
            }
        }

        guard !boundaryLayerIds.isEmpty else { return }
        let features = mapView.visibleFeatures(
            at: point,
            inStyleLayersWithIdentifiers: boundaryLayerIds
        )
        for feature in features {
            if let catalogId = feature.attribute(forKey: "catalog_id") as? String, !catalogId.isEmpty {
                listener?.onBoundaryClick(catalogId: catalogId)
                return
            }
        }
    }

    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
    ) -> Bool {
        true
    }

    private func renderMarkers(pointsJson: String) {
        if let existing = mapView.annotations {
            mapView.removeAnnotations(existing)
        }
        guard let data = pointsJson.data(using: .utf8),
              let points = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return }

        let annotations: [AchievementPointAnnotation] = points.compactMap { item in
            guard let id = item["id"] as? String,
                  let title = item["title"] as? String,
                  let subtitle = item["subtitle"] as? String,
                  let latitude = Self.doubleValue(item["latitude"]),
                  let longitude = Self.doubleValue(item["longitude"])
            else { return nil }
            let annotation = AchievementPointAnnotation()
            annotation.pointId = id
            annotation.title = title
            annotation.subtitle = subtitle
            annotation.coordinate = CLLocationCoordinate2D(latitude: latitude, longitude: longitude)
            annotation.isSearchSelection = (item["isSearchSelection"] as? Bool) ?? false
            return annotation
        }
        mapView.addAnnotations(annotations)
    }

    private func renderBoundaries(boundariesJson: String) {
        guard let style = mapView.style else { return }

        style.layers
            .map(\.identifier)
            .filter { $0.hasPrefix(Self.boundaryLayerPrefix) }
            .forEach { id in
                if let layer = style.layer(withIdentifier: id) {
                    style.removeLayer(layer)
                }
            }
        style.sources
            .map(\.identifier)
            .filter { $0.hasPrefix(Self.boundarySourcePrefix) }
            .forEach { id in
                if let source = style.source(withIdentifier: id) {
                    style.removeSource(source)
                }
            }
        boundaryLayerIds.removeAll()

        guard let data = boundariesJson.data(using: .utf8),
              let boundaries = try? JSONSerialization.jsonObject(with: data) as? [[String: Any]]
        else { return }

        for boundary in boundaries {
            guard let id = boundary["id"] as? String,
                  let geoJsonUrl = boundary["geoJsonUrl"] as? String,
                  let url = URL(string: geoJsonUrl)
            else { continue }

            let sourceId = Self.boundarySourcePrefix + id
            let lineId = Self.boundaryLayerPrefix + id + "-line"
            let fillId = Self.boundaryLayerPrefix + id + "-fill"
            let selectedLineId = Self.boundaryLayerPrefix + id + "-selected-line"

            let source = MLNShapeSource(identifier: sourceId, url: url, options: nil)
            style.addSource(source)

            let lineLayer = MLNLineStyleLayer(identifier: lineId, source: source)
            lineLayer.lineColor = NSExpression(forConstantValue: UIColor(red: 0.39, green: 0.45, blue: 0.55, alpha: 1))
            lineLayer.lineOpacity = NSExpression(forConstantValue: 0.72)
            lineLayer.lineWidth = NSExpression(forConstantValue: 1.1)
            style.addLayer(lineLayer)
            boundaryLayerIds.insert(lineId)

            if let selectedId = boundary["selectedCatalogId"] as? String, !selectedId.isEmpty {
                let fillLayer = MLNFillStyleLayer(identifier: fillId, source: source)
                fillLayer.predicate = NSPredicate(format: "catalog_id == %@", selectedId)
                fillLayer.fillColor = NSExpression(forConstantValue: UIColor(red: 0.15, green: 0.39, blue: 0.92, alpha: 1))
                fillLayer.fillOpacity = NSExpression(forConstantValue: 0.28)
                fillLayer.fillOutlineColor = NSExpression(forConstantValue: UIColor(red: 0.11, green: 0.31, blue: 0.85, alpha: 1))
                style.addLayer(fillLayer)
                boundaryLayerIds.insert(fillId)

                let selectedLine = MLNLineStyleLayer(identifier: selectedLineId, source: source)
                selectedLine.predicate = NSPredicate(format: "catalog_id == %@", selectedId)
                selectedLine.lineColor = NSExpression(forConstantValue: UIColor(red: 0.11, green: 0.31, blue: 0.85, alpha: 1))
                selectedLine.lineOpacity = NSExpression(forConstantValue: 0.95)
                selectedLine.lineWidth = NSExpression(forConstantValue: 3.0)
                style.addLayer(selectedLine)
                boundaryLayerIds.insert(selectedLineId)
            }
        }
    }

    private func emitViewportChanged() {
        let bounds = mapView.visibleCoordinateBounds
        let payload: [String: Any] = [
            "latitude": mapView.centerCoordinate.latitude,
            "longitude": mapView.centerCoordinate.longitude,
            "zoom": mapView.zoomLevel,
            "north": bounds.ne.latitude,
            "south": bounds.sw.latitude,
            "east": bounds.ne.longitude,
            "west": bounds.sw.longitude,
        ]
        guard let data = try? JSONSerialization.data(withJSONObject: payload),
              let json = String(data: data, encoding: .utf8)
        else { return }
        listener?.onViewportChanged(viewportJson: json)
    }

    private func applyViewport(viewportJson: String?) {
        suppressViewportCallback = true
        defer {
            // Cover Android easeCamera(550ms) before accepting idle viewport reports.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.65) { [weak self] in
                self?.suppressViewportCallback = false
            }
        }

        let apply = { [weak self] in
            guard let self else { return }
            let duration = Self.cameraAnimationDuration
            let timing = CAMediaTimingFunction(name: .easeInEaseOut)

            guard let viewportJson,
                  let data = viewportJson.data(using: .utf8),
                  let viewport = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let latitude = Self.doubleValue(viewport["latitude"]),
                  let longitude = Self.doubleValue(viewport["longitude"]),
                  let zoom = Self.doubleValue(viewport["zoom"])
            else {
                self.animateCamera(
                    to: CLLocationCoordinate2D(latitude: 20, longitude: 0),
                    zoomLevel: 1.2,
                    duration: duration,
                    timing: timing
                )
                return
            }

            if let north = Self.doubleValue(viewport["north"]),
               let south = Self.doubleValue(viewport["south"]),
               let east = Self.doubleValue(viewport["east"]),
               let west = Self.doubleValue(viewport["west"])
            {
                let bounds = MLNCoordinateBounds(
                    sw: CLLocationCoordinate2D(latitude: south, longitude: west),
                    ne: CLLocationCoordinate2D(latitude: north, longitude: east)
                )
                let padding = UIEdgeInsets(top: 48, left: 48, bottom: 48, right: 48)
                let camera = self.mapView.camera(thatFits: bounds, edgePadding: padding)
                self.mapView.setCamera(
                    camera,
                    withDuration: duration,
                    animationTimingFunction: timing
                )
            } else {
                self.animateCamera(
                    to: CLLocationCoordinate2D(latitude: latitude, longitude: longitude),
                    zoomLevel: zoom,
                    duration: duration,
                    timing: timing
                )
            }
        }

        if bounds.width == 0 || bounds.height == 0 {
            DispatchQueue.main.async(execute: apply)
        } else {
            apply()
        }
    }

    private func animateCamera(
        to coordinate: CLLocationCoordinate2D,
        zoomLevel: Double,
        duration: TimeInterval,
        timing: CAMediaTimingFunction
    ) {
        // Compute the target camera without a visible jump, then ease like Android.
        let fromCamera = mapView.camera
        mapView.setCenter(coordinate, zoomLevel: zoomLevel, animated: false)
        let toCamera = mapView.camera
        mapView.camera = fromCamera
        mapView.setCamera(
            toCamera,
            withDuration: duration,
            animationTimingFunction: timing
        )
    }

    private static func cameraKey(viewportJson: String?, cameraResetKey: Int64) -> String {
        "\(cameraResetKey)/\(viewportJson ?? "world")"
    }

    /// Matches Android `easeCamera(..., 550)`.
    private static let cameraAnimationDuration: TimeInterval = 0.55

    private static func doubleValue(_ value: Any?) -> Double? {
        switch value {
        case let number as Double: return number
        case let number as NSNumber: return number.doubleValue
        case let number as Int: return Double(number)
        default: return nil
        }
    }

    private static func makePinImage(color: UIColor) -> UIImage {
        let size = CGSize(width: 34, height: 42)
        let renderer = UIGraphicsImageRenderer(size: size)
        return renderer.image { _ in
            let centerX = size.width / 2
            let path = UIBezierPath()
            path.move(to: CGPoint(x: centerX, y: size.height - 1))
            path.addLine(to: CGPoint(x: centerX - 8, y: 23))
            path.addCurve(
                to: CGPoint(x: centerX, y: 5),
                controlPoint1: CGPoint(x: centerX - 14, y: 17),
                controlPoint2: CGPoint(x: centerX - 11, y: 5)
            )
            path.addCurve(
                to: CGPoint(x: centerX + 8, y: 23),
                controlPoint1: CGPoint(x: centerX + 11, y: 5),
                controlPoint2: CGPoint(x: centerX + 14, y: 17)
            )
            path.close()
            color.setFill()
            path.fill()
            UIColor.white.setFill()
            UIBezierPath(ovalIn: CGRect(x: centerX - 5, y: 9, width: 10, height: 10)).fill()
        }
    }

    private static let boundarySourcePrefix = "achievement-boundary-source-"
    private static let boundaryLayerPrefix = "achievement-boundary-layer-"
}

final class AchievementPointAnnotation: MLNPointAnnotation {
    var pointId: String = ""
    var isSearchSelection: Bool = false
}

/// Kotlin-facing handle registered from `iOSApp`.
final class AchievementMapHandleImpl: AchievementMapHandle {
    private let mapView = AchievementMapView(frame: .zero)

    func view() -> UIView {
        mapView
    }

    func setListener(listener: AchievementMapListener?) {
        mapView.listener = listener
    }

    func bind(
        styleUrl: String,
        pointsJson: String,
        boundariesJson: String,
        viewportJson: String?,
        cameraResetKey: Int64
    ) {
        mapView.bind(
            styleUrl: styleUrl,
            pointsJson: pointsJson,
            boundariesJson: boundariesJson,
            viewportJson: viewportJson,
            cameraResetKey: cameraResetKey
        )
    }
}

final class AchievementMapHandleFactoryImpl: AchievementMapHandleFactory {
    func create() -> AchievementMapHandle {
        AchievementMapHandleImpl()
    }
}

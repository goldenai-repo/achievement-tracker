from pathlib import Path

from app.catalog import parse_admin1, parse_country_info, parse_geonames_coordinates


def test_parse_geonames_country_and_admin1_files(tmp_path: Path) -> None:
    country_file = tmp_path / "countryInfo.txt"
    country_row = [
        "US",
        "USA",
        "840",
        "US",
        "United States",
        "Washington",
        "",
        "",
        "NA",
        ".us",
        "USD",
        "Dollar",
        "+1",
        "",
        "",
        "",
        "6252001",
        "CA,MX",
    ]
    country_file.write_text(
        "# comment\n"
        + "\t".join(country_row)
        + "\n",
        encoding="utf-8",
    )
    admin1_file = tmp_path / "admin1CodesASCII.txt"
    admin1_file.write_text(
        "US.CA\tCalifornia\tCalifornia\t5332921\nCN.31\tShanghai\tShanghai\t1796236\n",
        encoding="utf-8",
    )

    countries = parse_country_info(country_file, "test")
    admin1, rejected = parse_admin1(admin1_file, "test", {"US"})

    assert countries[0].id == "country:US"
    assert countries[0].metadata["iso3"] == "USA"
    assert [record.id for record in admin1] == ["admin1:US-CA"]
    assert rejected == [f"{admin1_file}:2: missing parent country CN"]


def test_parse_geonames_coordinates_by_source_id(tmp_path: Path) -> None:
    all_countries = tmp_path / "allCountries.txt"
    all_countries.write_text(
        "6252001\tUnited States\tUnited States\t\t38.0\t-97.0\tA\tPCLI\tUS\t\t\t\t\t\t\t\t\t\t\t\n"
        "5332921\tCalifornia\tCalifornia\t\t36.7783\t-119.4179\tA\tADM1\tUS\t\t\t\t\t\t\t\t\t\t\t\n",
        encoding="utf-8",
    )

    result = parse_geonames_coordinates(all_countries, {"6252001", "5332921", "missing"})

    assert result == {"6252001": (38.0, -97.0), "5332921": (36.7783, -119.4179)}

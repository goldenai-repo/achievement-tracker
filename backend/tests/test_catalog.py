from pathlib import Path

from app.catalog import parse_admin1, parse_country_info


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

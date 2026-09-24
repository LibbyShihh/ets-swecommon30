# Bundled GeoJSON Geometry schema

`Geometry.json` in this directory is an unmodified copy of the GeoJSON project's schema.

| | |
|---|---|
| Source | https://geojson.org/schema/Geometry.json |
| Retrieved | 2026-09-24 |
| SHA-256 | `152369d7eb6b3314ffc7deca1d66f9d498ed5b614ca38ed4e2c813fed01024fd` |
| License | MIT, see `license.md` |

## Why it is here

The OGC schema `sweCommon/3.0/json/Geometry.json` has a `value` property that `$ref`s the URL above.
Without a local copy, A.54 (`/conf/json-simple-components/schema-valid`) would need internet access on
every run. `JsonSimpleComponentsTest` maps that URL to this file (`schemaMappers.mapPrefix`).

## Updating

Download the file again, compare its SHA-256 with the one above, and update the table (date and hash)
if it changed. Do not edit `Geometry.json` by hand.

# HAB Card Generator

Generates printable membership card labels (Avery 64 x 34 mm, 24 labels per A4 sheet) for
Hoge Akkersbloei vzw from the member list exported by Assistonline.

Each label shows three lines:

```
2026                         637
        Tabita Claes
     Max Hermanlei 118
```

1. the membership year (left) and the member number (right) on a coloured background,
2. the member name,
3. street, house number and, when present, bus.

Labels are sorted by street so they can be delivered on foot, with options to print even and
odd house numbers separately and to put small streets last.

## Requirements

- Java 21 or newer (`java -version`).
- A member export from Assistonline in `.xlsx` format.
- Microsoft Word or LibreOffice to print the generated `.docx`.

## Installation

Download or build the distribution zip (see [Building](#building)) and unpack it. You get:

```
HAB-card-generator-<version>/
  HAB-card-generator-<version>.jar
  start.sh                 (Linux / macOS)
  start.bat                (Windows)
  config/application.yml   (settings, edit this file)
```

## Preparing the member list

1. In Assistonline select the members you want labels for (or all members) and export the
   member list as Excel. Every selected member becomes one label; there is no filtering in the tool.
2. Save the file in the folder configured as `inputDirectory` (see below).
   Every `.xlsx` or `.xls` file in that folder is read and merged, so keep only the export(s)
   you want to print there.

The export needs at least these columns (default header names shown; the names can be changed
in `config/application.yml`):

| Column            | Used for                                                        |
|-------------------|-----------------------------------------------------------------|
| `Lidnr.`          | member number on the label                                      |
| `Voornaam + naam` | name on the label                                               |
| `Adres`           | street, house number and bus, e.g. `Max Hermanlei 118`, `Langestraat 1B`, `Bredabaan 12 bus 3` |

Other columns in the export are ignored. The address is split automatically into street,
house number (`118`, `1B`, `75-77`) and bus (`bus 3`, also recognised as `12/3`). Rows whose
address has no house number are still printed (street only) and reported as a warning in the log.
Names written entirely in capitals (`JAN KERREMANS`) are converted to `Jan Kerremans`.

## Running

Linux / macOS:

```bash
./start.sh
```

Windows:

```bat
start.bat
```

The log ends with the location of the generated file, named
`generated_labels_<date>_<time>.docx` in `outputDirectory`. Open it in Word and print it on
Avery 64 x 34 mm label sheets (3 columns x 8 rows).

### Continuing on a partly used label sheet

Pass the number of labels already used on the first sheet as an argument, counted left to
right and top to bottom. Those positions stay empty:

```bash
./start.sh 5
```

### Running the jar directly

```bash
java -jar HAB-card-generator-<version>.jar --spring.config.location=file:config/application.yml 5
```

Any setting can also be overridden on the command line, for example
`--card.data.inputDirectory=/path/to/exports`.

## Configuration (`config/application.yml`)

```yaml
label:
  background:
    color: 9fd6e0            # background colour of the first label line, hex without #

card:
  data:
    year: "2026"             # printed top left on every label
    splitOddEven: true       # within a street, print even numbers first, then odd numbers
    useNewLine: true         # start a new label row when the street (or even/odd side) changes
    smallStreetsLast: false  # sort streets by number of members, largest first
    titleCaseUpperCaseNames: true   # JAN KERREMANS -> Jan Kerremans
    inputDirectory: "C:/members"    # folder with the Assistonline export(s)
    outputDirectory: "C:/members"   # folder where the .docx is written
    columns:                 # header names in the export
      memberId: 'Lidnr.'
      name: 'Voornaam + naam'
      address: 'Adres'
```

| Setting                   | Effect                                                                                                   |
|---------------------------|----------------------------------------------------------------------------------------------------------|
| `year`                    | Text printed top left on each label. Change it every membership year.                                   |
| `splitOddEven`            | Sort each street as even numbers ascending, then odd numbers ascending (one side of the street at a time). Off: plain ascending order. |
| `useNewLine`              | When the street changes (or the even/odd side changes), fill the rest of the 3-label row with empty labels so every street starts on a new row. |
| `smallStreetsLast`        | Sort streets by member count, largest street first, so the streets with few members end up on the last sheets. With this option streets with fewer than 4 members do not get a new row. |
| `titleCaseUpperCaseNames` | Convert names that are entirely upper case to title case. Names that already contain lower case letters are never changed. |
| `inputDirectory`          | Folder that is scanned for `.xlsx` / `.xls` files. Must exist.                                          |
| `outputDirectory`         | Folder for the generated `.docx`. Empty value: your `Downloads` folder.                                 |
| `columns.*`               | Header names of the member number, name and address columns. Matching is exact apart from surrounding spaces. If a column is not found the tool stops with an error that lists the headers it did find. |
| `label.background.color`  | Six-digit hex colour of the shaded first line, without `#`.                                              |

Street numbers sort on their leading number (`1B` sorts as 1, `75-77` as 75). Addresses without
a house number sort at the end of their street.

## Building

```bash
./mvnw clean install
```

This runs the tests, builds `target/HAB-card-generator-<version>.jar` and packs the
distribution zip `target/HAB-card-generator-<version>.zip` with the start scripts and
`config/application.yml`.

Run only the tests with `./mvnw test`.

## Troubleshooting

- `Input directory does not exist`: fix `inputDirectory` in `config/application.yml`. On Windows
  use forward slashes or double backslashes.
- `Column 'X' (card.data.columns....) not found in header`: the export uses a different header
  name. The message lists the headers that were found; adjust `columns` accordingly.
- `no house number found in address`: a warning per member whose address has no number. The
  label is still printed with only the street.
- Labels do not line up with the sheet: make sure Word prints at 100% scale without "fit to page",
  and that the skip argument matches the number of labels already used.

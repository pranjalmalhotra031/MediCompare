# MediCompare

A console application for searching Indian medicines and finding **possible**
alternatives that contain the same active ingredients in the same strength and
dosage form, according to two downloadable Kaggle datasets. It shows each
product's composition, strength, dosage form, manufacturer, use, pack and
dataset price.

> **MediCompare is not medical advice.** Possible matches are for review only.
> Always ask a doctor or pharmacist before changing a medicine.

## Data (only these two Kaggle datasets are used)

| File to place in `data/` | Dataset | Used for |
|---|---|---|
| `A_Z_medicines_dataset_of_India.csv` (**required**) | [A-Z Medicine Dataset of India](https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india) | the medicine list: name, composition, manufacturer, pack, price, discontinued flag |
| `medicine_dataset.csv` (optional) | [250K Medicines Usage, Side Effects and Substitutes](https://www.kaggle.com/datasets/shudhanshusingh/250k-medicines-usage-side-effects-and-substitutes) | use text, therapeutic class and listed substitute names |

The app works with only the first file; the second adds use text and a check of
the substitute names that file lists.

## Getting the data

Kaggle needs a free account, so the app cannot download the files for you.

**Browser:** sign in at kaggle.com, open each dataset page above, click
**Download**, unzip, and copy the `.csv` into the project's **`data/`** folder
(keep the file names exactly as in the table).

**Kaggle CLI (alternative):** save an API token as `~/.kaggle/kaggle.json`, then
```
pip install kaggle
kaggle datasets download -d shudhanshusingh/az-medicine-dataset-of-india --unzip -p data
kaggle datasets download -d shudhanshusingh/250k-medicines-usage-side-effects-and-substitutes --unzip -p data
```

**Record the version/date (recommended).** Kaggle CSVs carry no date. Copy
`data/dataset-info.properties.example` to `data/dataset-info.properties` and fill
in what the Kaggle pages show; the app then displays it. Otherwise it says
"dataset version/date not recorded".

The CSVs are git-ignored on purpose: redistribution terms depend on each
dataset's licence (see below), and one file is close to GitHub's 100 MB limit.

## Running

Requires Java 17+ and Maven 3.8+.

```
mvn -q compile exec:java
```

Type a medicine or ingredient (e.g. `augmentin`), then a result number for
details and possible matches. Keep searching without restarting; press Enter on
an empty search to quit. Tests: `mvn test`.

The app must start in the project root (so `data/` is found); you can also
pass another folder: `mvn -q compile exec:java -Dexec.args="/path/to/data"`.

With both files, startup (about 500,000 rows plus the match index) takes roughly
25-30 seconds and about 1.5 GB of memory. On `OutOfMemoryError`, set
`MAVEN_OPTS=-Xmx2g` (or `-Xmx2g` in IntelliJ's VM options). If `₹` shows as `?`,
start Java with `-Dstdout.encoding=UTF-8`.

## What each file contains (checked against the real files)

| File | Rows | Columns |
|---|---|---|
| `A_Z_medicines_dataset_of_India.csv` | 253,973 | `id, name, price(₹), Is_discontinued, manufacturer_name, type, pack_size_label, short_composition1, short_composition2` |
| `medicine_dataset.csv` | 248,218 | `id, name, substitute0-4, sideEffect0-41, use0-4, Chemical Class, Habit Forming, Therapeutic Class, Action Class` |

Facts that shape the design:

- The A-Z file has **no dosage-form and no strength column**. Strength is read
  from the composition text (`Amoxycillin (500mg)`); dosage form is parsed from
  the product name and pack label (`strip of 10 tablets`). Both are labelled
  "parsed" wherever they are shown. Its `type` column holds `allopathy` on every
  row, so it is not used.
- Some compositions have strength `NA` (about 5,400 entries); those are treated
  as **unknown**, and such products can only be "unverified".
- The usage file has no price, composition or dosage form. Its side-effect,
  chemical-class, habit-forming and action-class columns are deliberately not
  read. About 25,000 names repeat; the first row for a name is used.

## Matching rules

A candidate is a **close match** only if the dataset shows the same active
ingredients, the same strength for each ingredient, and the same dosage form.

| Label | Meaning |
|---|---|
| Close match | ingredients, strength per ingredient, and dosage form all agree |
| Partial - different strength | same ingredients and form, strength differs |
| Partial - different dosage form | same ingredients and strength, form differs |
| Unverified - required data missing | same ingredients, but a strength or dosage form is unknown on one side |

- Each ingredient keeps its own strength: `A 5mg + B 50mg` never matches
  `A 50mg + B 5mg`. Ingredient order does not matter.
- `1 gm` = `1000 mg`; `250 mcg` = `0.25 mg`. Other units (mg/5ml, %w/v, iu, ...)
  are compared as written. Names keep digits (Vitamin B12 is not Vitamin B6).
- Only a short list of true spelling variants is merged (amoxycillin/amoxicillin,
  acetaminophen/paracetamol, albuterol/salbutamol, cephalexin/cefalexin,
  frusemide/furosemide, lignocaine/lidocaine, rifampin/rifampicin). **Salts are
  not merged** (metoprolol succinate and tartrate stay different).
- Release markers (`SR`, `ER`/`XR`/`XL`, `CR`, `MR`, `PR`, `DT`, `MD`) found in the
  name or pack label are part of a tablet's or capsule's form, so a
  sustained-release tablet is never a close match for a plain tablet.
- If the product name and the pack label name **different** forms (e.g. "Syrup"
  vs "Oral Solution"), the form is left blank and the product is unverified
  rather than guessed. If one is just more specific ("Tablet" vs "tablet sr"),
  the more specific one is used.
- Matching is **text-based**. It does not check excipients, brand
  bioequivalence, regulatory status or clinical suitability.
- Within a level, results are ordered: discontinued products last, then lowest
  price per tablet/capsule (pack sizes differ, so pack prices alone are not
  comparable).
- Substitute names listed by the usage file are shown with a verdict from the
  same rules, looked up by exact name in the catalogue. Names not in the
  catalogue are shown as unverifiable.

## Prices and pharmacy comparison

- Prices are the dataset's listed values - **not current or live prices**. The
  file does not document whether it is MRP; per-tablet figures assume the price
  covers the whole listed pack.
- Prices are not sanity-checked. Some are clear outliers (e.g. a 10-tablet strip
  of an antibiotic combination listed at under ₹7), so the app warns when the
  lowest close match is far below the median of its peers.
- **Multi-pharmacy comparison is not implemented**: the data holds one price per
  product, not prices from several pharmacies. The app says so instead of
  inventing pharmacies, stock or savings.
- To add real comparison later, implement `PharmacyPriceProvider`
  (`quotesFor(Medicine)` returning timestamped `PharmacyQuote`s) against a feed
  you are **authorised** to use and pass it to `Main`; the result screen already
  prints quotes when a provider returns any.

## Data cleaning

Whitespace, BOM and control characters are tidied; ALL-CAPS or all-lowercase
names are Title-Cased for display (mixed case is left as supplied); exact
duplicates (same name, manufacturer, pack and composition) are dropped and
duplicates with a different price are counted; rows with no name, with more
fields than the header, or with unusable prices are handled and counted; a
non-UTF-8 file is read as Windows-1252; a broken quote stops loading at that
record with a warning and keeps the rows before it. A report is printed per
file. Missing values stay blank - nothing is invented.

## Attribution and licences

| Dataset | Source | Licence |
|---|---|---|
| A-Z Medicine Dataset of India | Kaggle, user `shudhanshusingh` - <https://www.kaggle.com/datasets/shudhanshusingh/az-medicine-dataset-of-india> | **TODO: copy the "License" shown on the Kaggle page** |
| 250K Medicines Usage, Side Effects and Substitutes | Kaggle, user `shudhanshusingh` - <https://www.kaggle.com/datasets/shudhanshusingh/250k-medicines-usage-side-effects-and-substitutes> | **TODO: copy the "License" shown on the Kaggle page** |

The licence cells are deliberately left as TODO: they must be read from the
dataset pages, not assumed. Kaggle datasets may themselves be compiled from other
sources; follow the dataset page's licence and credit the author when sharing.

## Limitations

- Datasets are snapshots, can contain errors, and are not maintained by this project.
- No availability, stock or location data, and no pharmacy-level prices.
- About 4% of catalogue records cannot be fully verified (unknown strength or
  no determinable dosage form) and appear as "unverified".
- Use text is attached only by **exact name match** (about 226,000 of 254,000
  medicines); substitute names are names only, not guaranteed equivalents.

## Project layout

```
src/main/java/com/medicompare/
  Main.java                  console flow and output wording
  Medicine.java, Ingredient.java, UsageInfo.java   data model
  CatalogLoader.java         reads the A-Z catalogue CSV
  UsageLoader.java           reads the usage/substitutes CSV
  CsvTable.java, LoadReport.java, DatasetInfo.java   CSV plumbing and reporting
  CompositionParser.java     "Amoxycillin (500mg)" -> ingredient + strength
  DosageForm.java            dosage form (and release markers) from text
  SubstituteFinder.java      match levels and ordering
  MedicineSearch.java        word-based search and ranking
  PriceNotes.java            price wording, per-tablet price, outlier warning
  PharmacyPriceProvider.java, PharmacyQuote.java, NoPharmacyPriceProvider.java
  TextUtil.java              text helpers
src/test/java/com/medicompare/   unit tests (all test data is synthetic)
```

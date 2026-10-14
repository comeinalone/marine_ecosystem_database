#!/usr/bin/env python3
"""Assemble the repo's existing MySQL schema and routines into a NEW demo DB."""
from pathlib import Path
import re,sys
repo=Path(sys.argv[1]).resolve() if len(sys.argv)>1 else Path.cwd()
here=Path(__file__).resolve().parent
files=['database/schema/01_create_tables.sql','database/advanced/01_views.sql','database/advanced/02_procedures.sql','database/advanced/03_functions.sql','database/advanced/04_triggers.sql']
missing=[s for s in files if not (repo/s).is_file()]
if missing:raise SystemExit('Missing repository source: '+', '.join(missing))
pieces=['-- Separate demo database: never touches Marine_Ecosystem_Database.\n']
for p in files:
    source=(repo/p).read_text()
    if re.search(r'\bDROP\s+DATABASE\b',source,re.I):raise SystemExit('Refusing destructive SQL in '+p)
    updated=re.sub('Marine_Ecosystem_Database','Marine_Ecosystem_Demo',source,flags=re.I)
    pieces.append('\n-- SOURCE: '+p+'\n'+updated+'\n')
pieces.append('\n-- SOURCE: synthetic demo dataset\n'+(here/'demo_seed.sql').read_text())
output=here/'marine_demo_full.sql';output.write_text('\n'.join(pieces))
print('Created '+str(output))
print('This creates only Marine_Ecosystem_Demo. Existing DB name untouched.')

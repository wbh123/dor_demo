#!/usr/bin/env python3
from pathlib import Path

path = Path('backend-java/server/src/test/java/com/wust/dormitory/mapper/AdminResidencyAdjustmentMapperMySqlIntegrationTest.java')
text = path.read_text(encoding='utf-8')

if 'CREATE TABLE campus (id BIGINT PRIMARY KEY, campus_name VARCHAR(128))' in text:
    print('admin residency adjustment fixture already contains campus context')
    raise SystemExit(0)

old_building = 'statement.execute("CREATE TABLE dormitory_building (id BIGINT PRIMARY KEY, building_code VARCHAR(32), building_name VARCHAR(128), enabled TINYINT)");'
new_building = 'statement.execute("CREATE TABLE campus (id BIGINT PRIMARY KEY, campus_name VARCHAR(128))");\n            statement.execute("CREATE TABLE dormitory_building (id BIGINT PRIMARY KEY, campus_id BIGINT, building_code VARCHAR(32), building_name VARCHAR(128), enabled TINYINT)");'
old_insert = 'statement.execute("INSERT INTO dormitory_building VALUES (1,\'B01\',\'一号楼\',1)");'
new_insert = 'statement.execute("INSERT INTO campus VALUES (1,\'主校区\')");\n            statement.execute("INSERT INTO dormitory_building VALUES (1,1,\'B01\',\'一号楼\',1)");'

if text.count(old_building) != 1:
    raise SystemExit(f'expected one legacy dormitory_building fixture definition, got {text.count(old_building)}')
if text.count(old_insert) != 1:
    raise SystemExit(f'expected one legacy dormitory_building fixture insert, got {text.count(old_insert)}')

text = text.replace(old_building, new_building, 1).replace(old_insert, new_insert, 1)
path.write_text(text, encoding='utf-8')
print('admin residency adjustment MySQL fixture updated with campus context')

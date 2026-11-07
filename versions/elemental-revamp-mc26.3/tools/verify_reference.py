"""Read-only checks of reference originals; writes evidence only in the V2 workspace."""
from pathlib import Path
import hashlib,json,datetime
from tower_nbt import load,positions,encode

root=Path(__file__).resolve().parents[1]
assert root==Path('C:/Users/Arjun/OneDrive/Desktop/ModMagicV2')
baseline=json.loads((root/'docs/reference-baseline.json').read_text())
reference=Path(baseline['source'])
changed=[]
for item in baseline['files']:
    path=reference/item['path']
    if not path.is_file() or hashlib.sha256(path.read_bytes()).hexdigest()!=item['sha256']: changed.append(item['path'])
structures=[]
for p in (root/'src/main/resources/data/circlemage/structure').glob('*.nbt'):
    _,old=load(reference/str(p.relative_to(root)).replace("structure", "structures"));_,new=load(p)
    old_solid=[encode(b) for pos,b,air in positions(old) if not air]
    new_solid=[encode(b) for pos,b,air in positions(new) if not air]
    assert old_solid==new_solid,p.name+' structural block data changed'
    for key in old.value:
        if key!='blocks': assert encode(old[key])==encode(new[key]),p.name+' changed '+key
    structures.append({'template':p.name,'nonAirBlocksUnchanged':len(new_solid),'metadataUnchanged':True})
assert len(structures)==4, "Expected all four tower templates"
resources=list((root/'src/main/resources').rglob('*.json'))
for p in resources: json.loads(p.read_text(encoding='utf-8-sig'))
result={'verified_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'reference':str(reference),
        'originalFilesChecked':len(baseline['files']),'originalFilesChanged':changed,
        'validResourceJsonFiles':len(resources),'structures':structures}
(root/'docs/reference-verification.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
assert not changed,'Reference originals changed since the initial copy'

"""Audit the tested 26.3 artifacts and copy them into dist; preserve the previous release."""
from pathlib import Path
import argparse,datetime,hashlib,json,shutil,zipfile,xml.etree.ElementTree as ET
parser=argparse.ArgumentParser()
parser.add_argument('--client-log',required=True)
parser.add_argument('--build-log',required=True)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
props=dict(line.split('=',1) for line in (root/'gradle.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
name=f"{props['archives_base_name']}-{props['mod_version']}"
client=root/args.client_log; build=root/args.build_log
assert 'BUILD SUCCESSFUL' in build.read_text(encoding='utf-8')
client_text=client.read_text(encoding='utf-8')
assert 'BUILD SUCCESSFUL' in client_text and 'CIRCLEMAGE_CLIENT_TEST_PASSED' in client_text
report_file=root/'build/gametest-results.xml'
all_tests=ET.parse(report_file).getroot().findall('.//testcase')
tests=[t for t in all_tests if t.get('name','').startswith('circlemage_tests:')]
assert len(tests)>=20 and not any(t.find('failure') is not None or t.find('error') is not None for t in all_tests),'Server tests failed or incomplete'
latest_source=max(p.stat().st_mtime for p in (root/'src').rglob('*') if p.is_file())
assert report_file.stat().st_mtime>=latest_source and client.stat().st_mtime>=latest_source,'Tests predate source edits'
baseline=json.loads((root/'docs/reference-baseline.json').read_text())
for item in baseline['files']:
    reference=Path(baseline['source'])/item['path']
    assert reference.is_file() and hashlib.sha256(reference.read_bytes()).hexdigest()==item['sha256'],'Reference changed: '+str(reference)
dest=root/'dist';dest.mkdir(exist_ok=True)
artifacts=[]
for suffix in ['.jar','-sources.jar']:
    source=root/'build/libs'/(name+suffix)
    assert source.stat().st_mtime>=latest_source
    with zipfile.ZipFile(source) as z:
        assert z.testzip() is None
        entries=z.namelist()
        assert not any('/test/' in n or n.startswith('data/circlemage_tests/') for n in entries),'Test files leaked into release'
        if suffix=='.jar':
            metadata=json.loads(z.read('fabric.mod.json').decode('utf-8-sig'))
            assert metadata['id']=='circlemage' and metadata['version']==props['mod_version']
            assert metadata['depends']['minecraft']=='~26.3' and metadata['depends']['java']=='>=25'
            assert int.from_bytes(z.read('com/example/circlemage/CircleMageMod.class')[6:8],'big')==69
            for structure in ['mage_tower','broken_tower_v1','broken_tower_v2','mage_tower_special']:
                assert f'data/circlemage/structure/{structure}.nbt' in entries
            for asset in ['items/wand.json','equipment/robe_amethyst.json','post_effect/desaturate.json','shaders/post/desaturate.fsh']:
                assert 'assets/circlemage/'+asset in entries
    target=dest/source.name;shutil.copyfile(source,target)
    sha=hashlib.sha256(source.read_bytes()).hexdigest()
    assert hashlib.sha256(target.read_bytes()).hexdigest()==sha
    artifacts.append({'file':source.name,'sha256':sha,'bytes':source.stat().st_size})
evidence=root/'docs/validation-26.3';evidence.mkdir(exist_ok=True)
shutil.copyfile(report_file,evidence/'gametest-results.xml')
shutil.copyfile(client,evidence/'client.log');shutil.copyfile(build,evidence/'build-server.log')
screens=root/'docs/screenshots-26.3';screens.mkdir(exist_ok=True)
image_names=['tree-circle3','journal','arcane-station','spell-and-npc-gallery','impact-desaturation']
for i,name in enumerate(image_names):
    source=root/'build/visualcheck26_3/screenshots'/f'{i:04d}_{name}.png'
    assert source.stat().st_mtime>=latest_source and source.stat().st_size>1000
    shutil.copyfile(source,screens/(name+'.png'))
report={'verified_utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'minecraft':props['minecraft_version'],
    'loader':props['loader_version'],'fabricApi':props['fabric_version'],'java':25,'version':props['mod_version'],
    'modGameTests':len(tests),'vanillaControlTests':len(all_tests)-len(tests),'failures':0,
    'clientGameTest':'passed; network casting, mana synchronization, menus, models, entity gallery and applied post effect',
    'screenshots':image_names,'artifacts':artifacts,'originalFilesChecked':len(baseline['files']),'originalFilesChanged':0,
    'installedInstanceModified':False,'limits':['No external multiplayer or third-party modpack test','No full survival playthrough or complete Master fight','Focused old data migration checks, not a full old-world migration'],
    'knownEnvironmentMessages':['Fabric test defaults log an invalid anisotropic-filter option before using the fallback','Offline development identity cannot authenticate with Realms/Minecraft services']}
(root/'docs/release-verification-26.3.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
(dest/'SHA256SUMS-26.3.txt').write_text(''.join(x['sha256']+'  '+x['file']+'\n' for x in artifacts),encoding='utf-8')
print(json.dumps(report,indent=2))

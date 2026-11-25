from pathlib import Path
from datetime import datetime
import shutil

stage = Path(__file__).parent / 'combined'
target = Path(r'C:\Users\minhq\OneDrive\Desktop\HCMUT\3\CNPM\demo\Smart-E-Mobility-Hub')
package = Path('src/main/java/vn/edu/bku/smart_emobility_backend')
files = ['mvnw.cmd', 'README.md', 'docs/03-api-contract.md', 'docs/04-data-schema.md',
         'docs/plans/feature-merge-student-auth-iot.md',
         str(package / 'service/StudentAccess.java'),
         str(package / 'common/config/SecurityConfiguration.java'),
         str(package / 'iot/StudentIoTAdapter.java'),
         'src/test/java/vn/edu/bku/smart_emobility_backend/StudentMergeIntegrationTest.java']
backup = target / '.merge-backups' / datetime.now().strftime('%Y%m%d-%H%M%S')
for name in files:
    dest = target / name
    if dest.exists():
        saved = backup / name
        saved.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(dest, saved)
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(stage / name, dest)
print('Updated', len(files), 'files in', target)
print('Backup:', backup)

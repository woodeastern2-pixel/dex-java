from pathlib import Path
import sys

root = Path(sys.argv[1])
manifest = root / 'app/src/main/AndroidManifest.xml'
m = manifest.read_text()
for name in ['MainActivity', 'SeniorActivity', 'RecordActivity', 'HistoryActivity', 'SettingsActivity']:
    m = m.replace(f'android:name=".{name}" android:exported="false"', f'android:name=".{name}" android:exported="true"')
manifest.write_text(m)

main = root / 'app/src/main/java/com/easternwood/dolbomon/MainActivity.java'
s = main.read_text()
hook = '''        if (BuildConfig.DEBUG && getIntent().getBooleanExtra("visual_seed", false)) {\n            DatabaseHelper d = DatabaseHelper.get(this);\n            if (d.listSeniors().isEmpty()) {\n                long park=d.addSenior("박철수","86","12","남성"); sleepSeed();\n                long lee=d.addSenior("이순자","91","5","여성"); sleepSeed();\n                long kim=d.addSenior("김영희","85","2","여성"); sleepSeed();\n                long hong=d.addSenior("홍길동","89","9","남성");\n                DatabaseHelper.CareRecord p=new DatabaseHelper.CareRecord(); p.seniorId=park; p.timestamp=System.currentTimeMillis(); p.meal="normal"; d.addRecord(p);\n                DatabaseHelper.CareRecord q=new DatabaseHelper.CareRecord(); q.seniorId=lee; q.timestamp=System.currentTimeMillis(); q.meal="good"; q.water="normal"; q.condition="good"; q.mood="calm"; q.sleep="normal"; q.bowel="none"; q.generated="이순자 어르신의 오늘 생활 소식입니다. 평소 일정대로 편안하게 지내셨습니다."; d.addRecord(q);\n                DatabaseHelper.CareRecord z=new DatabaseHelper.CareRecord(); z.seniorId=kim; z.timestamp=System.currentTimeMillis(); z.meal="good"; z.water="enough"; z.condition="good"; z.mood="happy"; z.sleep="good"; z.bowel="normal"; z.generated="김영희 어르신의 오늘 생활 소식입니다. 즐겁게 이야기를 나누며 하루를 보내셨습니다."; long zid=d.addRecord(z); d.addShareLog(zid,0,"SMS");\n                for(int i=1;i<=6;i++){ DatabaseHelper.CareRecord r=new DatabaseHelper.CareRecord(); r.seniorId=hong; r.timestamp=System.currentTimeMillis()-((i<=2?1:i<=4?2:i==5?3:4)*86400000L)-(i%2)*5400000L; r.meal=(i%2==0?"normal":"good"); r.water="normal"; r.condition="good"; r.mood="calm"; r.sleep="normal"; r.bowel="normal"; r.activities=(i==2?"reading":i==4?"program":""); r.generated="홍길동 어르신의 일일 기록 및 전달 내용입니다."; long rid=d.addRecord(r); if(i!=3)d.addShareLog(rid,0,"SMS"); }\n            }\n        }\n'''
if 'getBooleanExtra("visual_seed"' not in s:
    s = s.replace('        super.onCreate(savedInstanceState);\n', '        super.onCreate(savedInstanceState);\n' + hook, 1)
    s = s.replace('\n    @Override\n    protected void onNewIntent', '\n    private static void sleepSeed(){ try{Thread.sleep(8);}catch(Exception ignored){} }\n\n    @Override\n    protected void onNewIntent', 1)
main.write_text(s)

rec = root / 'app/src/main/java/com/easternwood/dolbomon/RecordActivity.java'
r = rec.read_text()
hook = '''        applyInitialStep();\n        if (BuildConfig.DEBUG && getIntent().hasExtra("visual_step")) {\n            int vs=getIntent().getIntExtra("visual_step",1);\n            if(mealGroup.getChildCount()>0)((Chip)mealGroup.getChildAt(0)).setChecked(true);\n            if(waterGroup.getChildCount()>1)((Chip)waterGroup.getChildAt(1)).setChecked(true);\n            if(conditionGroup.getChildCount()>0)((Chip)conditionGroup.getChildAt(0)).setChecked(true);\n            if(moodGroup.getChildCount()>0)((Chip)moodGroup.getChildAt(0)).setChecked(true);\n            if(sleepGroup.getChildCount()>0)((Chip)sleepGroup.getChildAt(0)).setChecked(true);\n            if(bowelGroup.getChildCount()>0)((Chip)bowelGroup.getChildAt(0)).setChecked(true);\n            noteEdit.setText("오늘은 평소 일정대로 지내셨고 대화와 프로그램에 즐겁게 참여하셨습니다.");\n            if(vs==3) generate(true); else showStep(vs);\n        }\n'''
r = r.replace('        applyInitialStep();\n', hook, 1)
rec.write_text(r)

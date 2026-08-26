#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])


def replace_once(path, old, new):
    p = root / path
    s = p.read_text()
    count = s.count(old)
    if count != 1:
        raise SystemExit(f'{path}: expected exactly one marker, found {count}: {old!r}')
    p.write_text(s.replace(old, new, 1))


replace_once('app/build.gradle', 'versionCode 26', 'versionCode 27')
replace_once('app/build.gradle', 'versionName "0.4.9"', 'versionName "0.5.0"')

replace_once('app/src/main/java/com/easternwood/dolbomon/MainActivity.java',
             'if (index > 0) lp.leftMargin = Ui.dp(this, 2);',
             'if (index > 0) lp.leftMargin = Ui.dp(this, 1);')
replace_once('app/src/main/java/com/easternwood/dolbomon/MainActivity.java',
             'if (index < 2) lp.rightMargin = Ui.dp(this, 2);',
             'if (index < 2) lp.rightMargin = Ui.dp(this, 1);')
replace_once('app/src/main/java/com/easternwood/dolbomon/MainActivity.java',
             'cardLp.topMargin = Ui.dp(this, 3);',
             'cardLp.topMargin = Ui.dp(this, 1);')

replace_once('app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java',
             'firstActionsLp.topMargin = Ui.dp(this, 8);',
             'firstActionsLp.topMargin = Ui.dp(this, 6);')
replace_once('app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java',
             'secondLp.topMargin = Ui.dp(this, 3);',
             'secondLp.topMargin = Ui.dp(this, 1);')
replace_once('app/src/main/java/com/easternwood/dolbomon/SeniorActivity.java',
             'int gap = Ui.dp(this, 1);',
             'int gap = Ui.dp(this, 0.5f);')

replace_once('app/src/main/java/com/easternwood/dolbomon/HistoryActivity.java',
             'recordLp.topMargin = Ui.dp(this, newDate ? 5 : 2);',
             'recordLp.topMargin = Ui.dp(this, newDate ? 3 : 1);')

record = 'app/src/main/java/com/easternwood/dolbomon/RecordActivity.java'
for old, new in [
    ('setCardTopMargin(activityCard, 7);', 'setCardTopMargin(activityCard, 2);'),
    ('setCardTopMargin(supplyCard, 7);', 'setCardTopMargin(supplyCard, 2);'),
    ('setCardTopMargin(specialCard, 7);', 'setCardTopMargin(specialCard, 2);'),
    ('setCardTopMargin(memoCard, 10);', 'setCardTopMargin(memoCard, 4);'),
    ('setCardTopMargin(photoCard, 10);', 'setCardTopMargin(photoCard, 4);'),
    ('setCardTopMargin(notice, 10);', 'setCardTopMargin(notice, 4);'),
    ('MaterialButton activityButton = Ui.secondaryButton(this, getString(R.string.choose_activities));',
     'MaterialButton activityButton = moreOptionsButton(getString(R.string.more_activities));'),
    ('MaterialButton supplyButton = Ui.secondaryButton(this, getString(R.string.choose_supplies));',
     'MaterialButton supplyButton = moreOptionsButton(getString(R.string.more_supplies));'),
    ('MaterialButton specialButton = Ui.secondaryButton(this, getString(R.string.choose_special));',
     'MaterialButton specialButton = moreOptionsButton(getString(R.string.more_special));'),
    ('smallActionLp.topMargin = Ui.dp(this, 7);', 'smallActionLp.topMargin = Ui.dp(this, 6);'),
    ('supplyLp.topMargin = Ui.dp(this, 7);', 'supplyLp.topMargin = Ui.dp(this, 6);'),
    ('specialLp.topMargin = Ui.dp(this, 7);', 'specialLp.topMargin = Ui.dp(this, 6);'),
]:
    replace_once(record, old, new)

helper_marker = '    private LinearLayout additionalHeader(int iconRes, CharSequence title, TextView summary) {'
helper = '''    private MaterialButton moreOptionsButton(CharSequence text) {
        MaterialButton button = new MaterialButton(this);
        button.setText(text);
        button.setTextSize(12.5f);
        button.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        button.setAllCaps(false);
        button.setTextColor(ContextCompat.getColor(this, R.color.brand_primary_dark));
        button.setCornerRadius(Ui.dp(this, 11));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setElevation(0);
        button.setStateListAnimator(null);
        button.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_green_bg)));
        button.setStrokeColor(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_soft_border)));
        button.setStrokeWidth(Ui.dp(this, 1));
        button.setIcon(ContextCompat.getDrawable(this, R.drawable.ic_chevron_right_24));
        button.setIconTint(ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_primary_dark)));
        button.setIconSize(Ui.dp(this, 18));
        button.setIconPadding(Ui.dp(this, 6));
        button.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_END);
        button.setContentDescription(text);
        return button;
    }

'''
replace_once(record, helper_marker, helper + helper_marker)

strings = {
    'app/src/main/res/values/strings.xml': [
        '<string name="more_activities">더 많은 활동 보기</string>',
        '<string name="more_supplies">더 많은 필요물품 보기</string>',
        '<string name="more_special">더 많은 특이사항 보기</string>',
    ],
    'app/src/main/res/values-en/strings.xml': [
        '<string name="more_activities">See more activity options</string>',
        '<string name="more_supplies">See more supply options</string>',
        '<string name="more_special">See more special-note options</string>',
    ],
    'app/src/main/res/values-zh/strings.xml': [
        '<string name="more_activities">查看更多活动选项</string>',
        '<string name="more_supplies">查看更多用品选项</string>',
        '<string name="more_special">查看更多特别事项选项</string>',
    ],
    'app/src/main/res/values-vi/strings.xml': [
        '<string name="more_activities">Xem thêm lựa chọn hoạt động</string>',
        '<string name="more_supplies">Xem thêm lựa chọn vật dụng</string>',
        '<string name="more_special">Xem thêm lựa chọn ghi chú đặc biệt</string>',
    ],
}
for rel, rows in strings.items():
    p = root / rel
    s = p.read_text()
    if 'name="more_activities"' in s:
        raise SystemExit(f'{rel}: v0.5.0 strings already exist unexpectedly')
    block = '    ' + '\n    '.join(rows) + '\n'
    if s.count('</resources>') != 1:
        raise SystemExit(f'{rel}: resources closing tag mismatch')
    p.write_text(s.replace('</resources>', block + '</resources>', 1))

print(f'DolbomOn v0.5.0 final polish applied at {root}')

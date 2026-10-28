#!/usr/bin/env python3
"""Verify ancient test-fixture checks reject corruption without editing real resources."""
from pathlib import Path
import tempfile,shutil,subprocess,unittest,re
ROOT=Path(__file__).resolve().parents[1]
class FixtureVerifierTests(unittest.TestCase):
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory(prefix='sora-locale-verifier-');self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
  paths=['common/src/main/java/jp/co/soramitsu/common/resourses/LanguagesHolder.kt','scripts/verify-middle-egyptian-localization.mjs','common/build.gradle.kts','app/build.gradle.kts','common/src/main/res/values','app/src/main/res/values','common/src/nonProduction/res/values-b+egy+Egyp','app/src/nonProduction/res/values-b+egy+Egyp','common/src/nonProduction/res/font','common/src/nonProduction/assets/licenses','common/src/develop/res/xml/locale_config.xml','common/src/soralution/res/xml/locale_config.xml','common/src/production/res/xml/locale_config.xml']
  for name in paths:
   src=ROOT/name;dst=self.root/name;dst.parent.mkdir(parents=True,exist_ok=True)
   if src.is_dir():shutil.copytree(src,dst)
   else:shutil.copy2(src,dst)
 def run_check(self):return subprocess.run(['node',str(self.root/'scripts/verify-middle-egyptian-localization.mjs')],capture_output=True,text=True)
 def change(self,name,old,new):
  p=self.root/name;s=p.read_text();self.assertIn(old,s);p.write_text(s.replace(old,new,1))
 def rejected(self,message):
  r=self.run_check();self.assertNotEqual(r.returncode,0);self.assertIn(message,r.stderr)
 def test_real_fixture_passes(self):
  r=self.run_check();self.assertEqual(r.returncode,0,r.stderr);self.assertIn('explicitly declared English fallback',r.stdout)
 def test_native_latin_rejected(self):
  p=self.root/'common/src/nonProduction/res/values-b+egy+Egyp/strings.xml';s=p.read_text();s=s.replace('</string>','broken</string>',1);p.write_text(s);self.rejected('untranslated Latin text')
 def test_native_number_change_rejected(self):
  self.change('common/src/nonProduction/res/values-b+egy+Egyp/strings.xml','12, 15, 18, 21','13, 15, 18, 21');self.rejected('numeric constraint mismatch')
 def test_fallback_drift_rejected(self):
  self.change('common/src/nonProduction/res/values-b+egy+Egyp/english_fallback.xml','Reorder item','Different item');self.rejected('fallback is stale or altered')
 def test_fallback_placeholder_change_rejected(self):
  self.change('common/src/nonProduction/res/values-b+egy+Egyp/english_fallback.xml','%1$s','%9$s');self.rejected('placeholder mismatch')
 def test_missing_fallback_entry_rejected(self):
  p=self.root/'common/src/nonProduction/res/values-b+egy+Egyp/english_fallback.xml';s=p.read_text();s=re.sub(r'<string name="asset_reorder">.*?</string>','',s);p.write_text(s);self.rejected('resource mismatch')
 def test_missing_fallback_notice_rejected(self):
  self.change('common/src/nonProduction/res/values-b+egy+Egyp/english_fallback.xml','They are not Akkadian/Egyptian translations and need native review.','');self.rejected('fallback notice missing')
 def test_font_change_rejected(self):
  p=self.root/'common/src/nonProduction/res/font/noto_sans_egyptian_hieroglyphs_regular.ttf';p.write_bytes(p.read_bytes()+b'!');self.rejected('unmodified Noto')
 def test_license_change_rejected(self):
  p=self.root/'common/src/nonProduction/assets/licenses/noto_sans_egyptian_hieroglyphs_ofl.txt';p.write_text('changed');self.rejected('matching unmodified SIL')
 def test_font_alias_change_rejected(self):
  self.change('common/src/nonProduction/res/values-b+egy+Egyp/hieroglyph_font_aliases.xml','@font/noto_sans_egyptian_hieroglyphs_regular','@font/wrong');self.rejected('alias sora_thin')
 def test_main_fixture_directory_rejected(self):
  (self.root/'common/src/main/res/values-b+egy+Egyp').mkdir();self.rejected('still packaged in the main source set')
 def test_production_locale_registration_rejected(self):
  self.change('common/src/production/res/xml/locale_config.xml','</locale-config>','<locale android:name="egy-Egyp" /></locale-config>');self.rejected('must not be offered in production')
 def test_develop_locale_removal_rejected(self):
  self.change('common/src/develop/res/xml/locale_config.xml','<locale android:name="egy-Egyp" />','');self.rejected('is not registered')
 def test_shared_resources_removal_rejected(self):
  self.change('common/build.gradle.kts','getByName("develop").res.directories.add(file("src/nonProduction/res").absolutePath)','');self.rejected('must retain ancient fixture')
 def test_app_shared_resources_removal_rejected(self):
  self.change('app/build.gradle.kts','res.directories.add(file("src/nonProduction/res").absolutePath)','');self.rejected('app: develop/soralution')
if __name__=='__main__':unittest.main()

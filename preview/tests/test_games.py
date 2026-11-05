import unittest,json,pathlib
root=pathlib.Path(__file__).resolve().parents[1]
class GamesCatalogue(unittest.TestCase):
 def test_preserves_originals_and_has_verified_games(self):
  data=json.loads((root/'app/src/main/assets/catalogue-v1.json').read_text())['entries']
  self.assertEqual(90,len(data));self.assertEqual(50,len([e for e in data if e['category']!='Games']))
  games=[e for e in data if e['category']=='Games'];self.assertEqual(40,len(games))
  self.assertEqual(1,len([e for e in games if e['classification']=='Unsupported']))
  evidence=json.loads((root/'release/GAMES-VERIFICATION.json').read_text())
  for e in games:
   self.assertTrue(any(x['url']==e['url'] and x.get('status')==200 for x in evidence))
   self.assertEqual('2026-11-05',e['verifiedDate']);self.assertNotIn('offline',e['description'].lower())
if __name__=='__main__':unittest.main()

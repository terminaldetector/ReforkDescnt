import json
from pathlib import Path
import tempfile
import unittest
import audit_resources as audit

class ResourceGraphTest(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
    def model(self,name,data):
        path=self.root/'src/main/resources/assets/drmd/models'/f'{name}.json';path.parent.mkdir(parents=True,exist_ok=True);path.write_text(json.dumps(data));return path
    def test_vanilla_parent_is_external_not_a_false_missing_local_asset(self):
        self.model('item/tool',dict(parent='minecraft:item/generated',textures=dict(layer0='minecraft:item/iron_ingot')))
        self.assertFalse(audit.scan(self.root)['errors'])
    def test_missing_custom_parent_and_texture_are_errors(self):
        self.model('item/tool',dict(parent='drmd:item/absent',textures=dict(layer0='drmd:item/absent')))
        self.assertEqual(2,len(audit.scan(self.root)['errors']))
    def test_parent_cycles_and_texture_cycles_cannot_pass(self):
        self.model('item/a',dict(parent='drmd:item/b',textures=dict(a='#b',b='#a')))
        self.model('item/b',dict(parent='drmd:item/a'))
        self.assertTrue(audit.scan(self.root)['errors'])
    def test_nonfinite_display_transform_is_rejected(self):
        self.model('item/a',dict(display=dict(gui=dict(scale=[1,float('nan'),1]))))
        self.assertTrue(audit.scan(self.root)['errors'])
    def test_inherited_texture_variables_resolve(self):
        self.model('item/base',dict(textures=dict(all='minecraft:block/stone')))
        self.model('item/tool',dict(parent='drmd:item/base',textures=dict(particle='#all')))
        self.assertFalse(audit.scan(self.root)['errors'])
    def test_shared_geometry_is_a_warning_that_preserves_saved_ids(self):
        for name in ('a','b'):self.model('item/'+name,dict(parent='minecraft:item/generated'))
        result=audit.scan(self.root);self.assertFalse(result['errors']);self.assertEqual(1,len(result['duplicate_geometry']));self.assertEqual(2,len(result['items']))

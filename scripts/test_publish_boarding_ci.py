import copy
import unittest
from publish_boarding_ci import VERSION, validate_proof


class BoardingPackageProofTest(unittest.TestCase):
    def proof(self):
        return dict(source_sha='a' * 40, version=VERSION,
                    unit=dict(tests=657), gametest=dict(tests=209),
                    hull_contacts=dict(actualHullPairs=True,wholeTickIntegrated=True,sequentialThreeHulls=True,rotatedHullImpulse=True),
                    body_combat=dict(actualHitscanDamage=True,actualProjectileDamage=True,actualRadialDamage=True),
                    body_fractures=dict(actualFragments=True,continuousMaterialMotion=True,fastFragmentTickIntegrated=True,fragmentNbtRoundTrip=True),
                    jar=f'drmd-6dof-{VERSION}-fabric-1.21.1.jar')

    def test_exact_tested_candidate_is_accepted(self):
        validate_proof(self.proof(), 'a' * 40)

    def test_old_or_partial_contact_proof_is_not_a_physical_hull_release(self):
        p=self.proof();p.pop('hull_contacts')
        with self.assertRaisesRegex(ValueError,'Actual physical hull contact proof'):validate_proof(p,'a'*40)
        for key in ('actualHullPairs','wholeTickIntegrated','sequentialThreeHulls','rotatedHullImpulse'):
            with self.subTest(key=key):
                p=self.proof();p['hull_contacts'][key]=False
                with self.assertRaisesRegex(ValueError,'Actual physical hull contact proof'):validate_proof(p,'a'*40)

    def test_contact_only_release_cannot_be_relabelled_as_fracture(self):
        p=self.proof();p.pop('body_fractures')
        with self.assertRaisesRegex(ValueError,'Physical fragment client proof'):validate_proof(p,'a'*40)
        for key in ('actualFragments','continuousMaterialMotion','fastFragmentTickIntegrated','fragmentNbtRoundTrip'):
            p=self.proof();p['body_fractures'][key]=False
            with self.assertRaisesRegex(ValueError,'Physical fragment client proof'):validate_proof(p,'a'*40)

    def test_local_edit_only_fractures_cannot_be_published_as_combat(self):
        p=self.proof();p.pop('body_combat')
        with self.assertRaisesRegex(ValueError,'Actual hull combat proof'):validate_proof(p,'a'*40)
        for key in ('actualHitscanDamage','actualProjectileDamage','actualRadialDamage'):
            p=self.proof();p['body_combat'][key]=False
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'Actual hull combat proof'):validate_proof(p,'a'*40)

    def test_other_source_version_or_incomplete_tests_cannot_be_published(self):
        for field, value in (('source_sha', 'b' * 40), ('version', '1.1.23-snapshot.1'),
                             ('jar', '../other.jar'), ('unit', dict(tests=617)),
                             ('gametest', dict(tests=158))):
            with self.subTest(field=field), self.assertRaises(ValueError):
                p = copy.deepcopy(self.proof())
                p[field] = value
                validate_proof(p, 'a' * 40)


if __name__ == '__main__':
    unittest.main()

import copy
import unittest
from publish_boarding_ci import VERSION, validate_proof


class BoardingPackageProofTest(unittest.TestCase):
    def proof(self):
        return dict(source_sha='a' * 40, version=VERSION,
                    unit=dict(tests=633), gametest=dict(tests=183),
                    jar=f'drmd-6dof-{VERSION}-fabric-1.21.1.jar')

    def test_exact_tested_candidate_is_accepted(self):
        validate_proof(self.proof(), 'a' * 40)

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

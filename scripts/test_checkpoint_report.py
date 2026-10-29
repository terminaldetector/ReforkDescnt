"""Regression checks for the release evidence gate, using temporary synthetic fixtures."""
import json
import io
import os
from pathlib import Path
import subprocess
import tempfile
import time
import unittest
from unittest.mock import patch
import zipfile

import checkpoint_report as checkpoint


class EvidenceGateTest(unittest.TestCase):
    def setUp(self):
        # Synthetic release fixtures must never produce GitHub workflow annotations.
        output = patch('sys.stdout', new_callable=io.StringIO)
        output.start()
        self.addCleanup(output.stop)
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.started = int(time.time()) - 2
        self.write('gradle.properties', 'mod_version=test\narchives_base_name=drmd-6dof\n')
        self.write('build/checkpoint/started-at.txt', str(self.started))
        self.write('build/checkpoint/SOURCE_SHA.txt', 'abc123\n')
        self.write('dist/INSTALL_TLAUNCHER.txt', 'Test fixture, not a playable mod.\n')
        for folder in ('test-results/test', 'gametest/reports'):
            self.write(f'build/{folder}/tests.xml', '<testsuite><testcase name="ok"/></testsuite>')
        for name in checkpoint.MARKERS:
            self.write(f'build/rendercheck/{name}.txt', 'Fixture marker\n')
        self.write('build/rendercheck/swarm-ai-ok.txt',
                   'Live swarm synchronized 128 drones; indexed=128; moving=96; maximum candidates/query=96.\n')
        for name in checkpoint.FRAMES:
            self.write(f'build/rendercheck/screenshots/{name}.png', b'\x89PNG\r\n\x1a\nfixture')
        for name in checkpoint.SHOT_FRAMES:
            self.write(f'build/shootcheck/screenshots/{name}.png', b'\x89PNG\r\n\x1a\nfixture')
        self.write('build/shootcheck/shootcheck-ok.json', json.dumps(dict(scenes=10, fovs=[70,80,90],
                   rangePixelsChecked=True, fastSpeed=8, fastTravel=32, fastFrames=3,
                   fastPixelsChecked=True, muzzleEvents=2, impactEvents=2)))
        for name in checkpoint.HYBRID_FRAMES:
            self.write(f'build/shootcheck/screenshots/{name}.png', b'\x89PNG\r\n\x1a\nfixture')
        self.write('build/shootcheck/hybrid-weapons-ok.json',json.dumps(dict(models=3,presetRoundTrips=3,
                   serverSaves=3,muzzlePairs=3,fovs=[70,80,90],banked=3,vertical=3,realUseAnimated=3,loadSynced=True,external=3,frames=40)))
        jar = self.root / 'build/libs/drmd-6dof-test.jar'
        jar.parent.mkdir(parents=True)
        with zipfile.ZipFile(jar, 'w') as archive:
            archive.writestr('fabric.mod.json', json.dumps(dict(id='drmd', version='test', entrypoints={})))
            archive.writestr('com/terminaldetector/drmd/DescentMod.class', b'fixture')
        self.git = patch.object(subprocess, 'check_output', return_value='abc123\n')
        self.git.start()
        self.addCleanup(self.git.stop)

    def write(self, name, contents):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(contents if isinstance(contents, bytes) else contents.encode())
        return path

    def test_complete_run_stages_one_jar_with_hash(self):
        checkpoint.verify(self.root)
        out = self.root / 'build/checkpoint'
        proof = json.loads((out / 'verified.json').read_text())
        jars = list((out / 'distribution').glob('*.jar'))
        self.assertEqual(1, len(jars))
        self.assertEqual(checkpoint.sha256(jars[0]), proof['sha256'])
        self.assertEqual('abc123', proof['source_sha'])

    def test_installation_uses_verified_filename_version_and_digest(self):
        self.write('dist/INSTALL_TLAUNCHER.txt', 'Obsolete release: drmd-6dof-1.1.11.jar\n')
        checkpoint.verify(self.root)
        out=self.root/'build/checkpoint'
        proof=json.loads((out/'verified.json').read_text())
        install=(out/'distribution/INSTALL_TLAUNCHER.txt').read_text()
        self.assertIn('DRMD test',install)
        self.assertIn(proof['jar'],install)
        self.assertIn(proof['sha256'],install)
        self.assertNotIn('1.1.11',install)

    def test_stale_marker_is_rejected(self):
        path = self.root / 'build/rendercheck/collapse-ok.txt'
        os.utime(path, (self.started - 10, self.started - 10))
        with self.assertRaisesRegex(ValueError, 'Stale evidence'):
            checkpoint.verify(self.root)

    def test_missing_frame_is_rejected(self):
        (self.root / 'build/rendercheck/screenshots/drone-swarm-flight.png').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing or empty evidence'):
            checkpoint.verify(self.root)

    def test_incomplete_swarm_is_rejected(self):
        self.write('build/rendercheck/swarm-ai-ok.txt',
                   'Live swarm synchronized 128 drones; indexed=128; moving=95; maximum candidates/query=96.\n')
        with self.assertRaisesRegex(ValueError, 'Swarm criteria failed'):
            checkpoint.verify(self.root)

    def test_test_harness_in_release_is_rejected(self):
        with zipfile.ZipFile(self.root / 'build/libs/drmd-6dof-test.jar', 'a') as archive:
            archive.writestr('com/terminaldetector/drmd/HybridRenderCheck.class', b'fixture')
        with self.assertRaisesRegex(ValueError, 'Test-only code'):
            checkpoint.verify(self.root)

    def test_no_unit_tests_is_not_a_pass(self):
        self.write('build/test-results/test/tests.xml', '<testsuite/>')
        with self.assertRaisesRegex(ValueError, 'incomplete or failing test report'):
            checkpoint.verify(self.root)

    def test_no_weapon_verification_is_rejected(self):
        (self.root / 'build/shootcheck/shootcheck-ok.json').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing or empty evidence'):
            checkpoint.verify(self.root)

    def test_coordinates_without_visible_fast_shot_are_not_a_pass(self):
        path = self.root / 'build/shootcheck/shootcheck-ok.json'
        proof = json.loads(path.read_text())
        proof['fastPixelsChecked'] = False
        path.write_text(json.dumps(proof))
        with self.assertRaisesRegex(ValueError, 'Incomplete weapon verification'):
            checkpoint.verify(self.root)

    def test_missing_hybrid_verification_is_rejected(self):
        (self.root / 'build/shootcheck/hybrid-weapons-ok.json').unlink()
        with self.assertRaisesRegex(ValueError, 'Missing or empty evidence'):
            checkpoint.verify(self.root)

    def test_unsaved_or_unanimated_hybrid_is_rejected(self):
        self.write('build/shootcheck/hybrid-weapons-ok.json',json.dumps(dict(models=3,frames=37)))
        with self.assertRaisesRegex(ValueError, 'Incomplete hybrid verification'):
            checkpoint.verify(self.root)

    def audit_fixture(self):
        proof=dict(lifecycleRoundTrips=8,gravityAxes=6,gravityRelease=True,lowerRecovery=True,
                   modalHud=True,inventoryDistinct=True,settingsScales=[2,3,4],horizonCases=168,
                   horizonOnPixels=True,swarmProfiles=[128,256,512,1024],environmentComparisons=4,profileCombinations=4,frames=369,dhInstalled=False)
        proof['settingsViewports']=[dict(requested=n,actual=n,width=1920 if n==4 else 1280,height=1080 if n==4 else 720) for n in (2,3,4)]
        self.write('build/shootcheck/audit-ok.json',json.dumps(proof))
        for name in checkpoint.AUDIT_FRAMES:
            self.write(f'build/shootcheck/screenshots/{name}.png',b'\x89PNG\r\n\x1a\nfixture')
        for height in (100,320,640,880,1024,1500,2150):
            for bank in (0,90,180):
                for speed in (0,50,300,628):
                    for dh in (0,1):
                        for view in ('on','off'):
                            self.write(f'build/shootcheck/screenshots/audit-horizon-{height}-{bank}-{speed}-{dh}-{view}.png',b'\x89PNG\r\n\x1a\nfixture')
        header='requested,server_alive,client_synced,moving,exact_overlaps,max_candidates,server_work_mean_ms,server_work_p95_ms,tps,frames,fps,frame_p95_ms,cleanup_remaining'
        rows=[f'{count},{count},{count},{count},0,96,10,15,20,60,30,34,0' for count in (128,256,512,1024)]
        self.write('build/shootcheck/swarm-profile.csv','\n'.join([header]+rows)+'\n')
        self.write('build/shootcheck/inventory-models.tsv','fixture model fingerprints, not actual rendered evidence')
        return proof

    def test_complete_audit_requires_real_matrix_and_load_profile_files(self):
        self.audit_fixture()
        result=checkpoint.verify_audit(self.root,self.started)
        self.assertEqual(4,len(result['profiles']))
        self.assertEqual(168,result['proof']['horizonCases'])

    def test_audit_coordinates_without_horizon_pixels_are_rejected(self):
        proof=self.audit_fixture();proof['horizonOnPixels']=False
        self.write('build/shootcheck/audit-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Incomplete video audit'):
            checkpoint.verify_audit(self.root,self.started)

    def test_one_missing_horizon_matrix_frame_rejects_the_audit(self):
        self.audit_fixture()
        (self.root/'build/shootcheck/screenshots/audit-horizon-2150-180-628-1-on.png').unlink()
        with self.assertRaisesRegex(ValueError,'Missing or empty evidence'):
            checkpoint.verify_audit(self.root,self.started)

    def test_unmeasured_or_stalled_swarm_is_not_a_profile(self):
        self.audit_fixture()
        path=self.root/'build/shootcheck/swarm-profile.csv'
        path.write_text(path.read_text().replace('128,128,128,128,','128,128,128,95,'))
        with self.assertRaisesRegex(ValueError,'128-drone baseline stalled'):
            checkpoint.verify_audit(self.root,self.started)

    def test_non_png_audit_frame_is_rejected(self):
        self.audit_fixture()
        self.write('build/shootcheck/screenshots/audit-inventory.png','not an image')
        with self.assertRaisesRegex(ValueError,'Invalid PNG evidence'):
            checkpoint.verify_audit(self.root,self.started)

    def test_non_finite_swarm_timing_is_rejected(self):
        self.audit_fixture()
        path=self.root/'build/shootcheck/swarm-profile.csv'
        path.write_text(path.read_text().replace(',10,15,20,',',nan,15,20,'))
        with self.assertRaisesRegex(ValueError,'Invalid measured swarm metric'):
            checkpoint.verify_audit(self.root,self.started)

    def test_requested_gui_scale_cannot_replace_actual_scale(self):
        proof=self.audit_fixture();proof['settingsViewports'][2]['actual']=3
        self.write('build/shootcheck/audit-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'actual viewport'):
            checkpoint.verify_audit(self.root,self.started)

    def test_native_audit_cannot_be_replaced_by_unrelated_passing_tests(self):
        with self.assertRaisesRegex(ValueError,'Missing native audit GameTests'):
            checkpoint.verify_native_audit(self.root,self.started)

    def test_native_audit_accepts_actual_gametest_identifier_case(self):
        cases=''.join('<testcase name="drmd.audit.'+name.lower()+'"/>' for name in checkpoint.AUDIT_NATIVE_CASES)
        self.write('build/gametest/reports/audit.xml','<testsuite>'+cases+'</testsuite>')
        self.write('build/gametest/item-resource-inventory.tsv','synthetic registry table, not real game evidence')
        checkpoint.verify_native_audit(self.root,self.started)

    def fragment_boarding_fixture(self):
        names=checkpoint.BOARDING_SCENES[:3]+('fragment-contact',)+checkpoint.BOARDING_SCENES[3:]
        proof=dict(actualInputs=True,authoritativeContact=True,worldUpOnly=True,sameStepJump=True,teleportFollow=False,
            jumpWorldVelocityError=0.,maxPredictionError=.02,jumpReleaseNetworkError=0.,predictionSamples=100,
            fragmentContact=True,fragmentSpeedError=.1,scenes=[dict(scene=name,frames=3) for name in names])
        for name in names:self.write(f'build/fragmentboardingcheck/boarding-frames/{name}.png',b'\x89PNG\r\n\x1a\nfixture')
        self.write('build/fragmentboardingcheck/boarding-ok.json',json.dumps(proof));return proof

    def test_fragment_boarding_requires_the_actual_support_transition(self):
        proof=self.fragment_boarding_fixture();checkpoint.verify_fragment_boarding(self.root,self.started)
        proof['fragmentContact']=False;self.write('build/fragmentboardingcheck/boarding-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Missing player fragment contact'):checkpoint.verify_fragment_boarding(self.root,self.started)

    def test_fragment_boarding_rejects_speed_loss_and_invalid_metrics(self):
        proof=self.fragment_boarding_fixture()
        for value in (.2,-1,float('nan'),float('inf'),True):
            p=proof.copy();p['fragmentSpeedError']=value;self.write('build/fragmentboardingcheck/boarding-ok.json',json.dumps(p))
            with self.subTest(value=value),self.assertRaisesRegex(ValueError,'Invalid player fragment speed'):checkpoint.verify_fragment_boarding(self.root,self.started)

    def test_original_boarding_frames_cannot_certify_fragment_handoff(self):
        proof=self.fragment_boarding_fixture();proof['scenes']=[dict(scene=name,frames=3) for name in checkpoint.BOARDING_SCENES]
        self.write('build/fragmentboardingcheck/boarding-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Missing completed boarding scenes'):checkpoint.verify_fragment_boarding(self.root,self.started)

    def hull_contact_fixture(self):
        proof=dict(orientedTerrain=True,hollowRay=True,unknownNativeYRejected=True,
            compoundContactQuery=True,relativeCCD=True,pairReaction=True,nativeGravitySupport=True,
            actualRenderer=True,actualHullPairs=True,wholeTickIntegrated=True,sequentialThreeHulls=True,
            rotatedHullImpulse=True,stableRenderedRotationFrames=60,pairClientSamples=350,
            maxPairClientError=0.,scenes=[dict(scene=name,renderedFrames=90) for name in checkpoint.HULL_CONTACT_SCENES])
        for name in checkpoint.HULL_CONTACT_SCENES:
            self.write(f'build/hullcontactcheck/body-contact-frames/{name}.png',b'\x89PNG\r\n\x1a\nfixture')
        self.write('build/hullcontactcheck/body-contacts-ok.json',json.dumps(proof));return proof

    def test_hull_contact_gate_requires_real_pairs_and_complete_tick(self):
        proof=self.hull_contact_fixture()
        checkpoint.verify_hull_contacts(self.root,self.started)
        for key in ('actualHullPairs','wholeTickIntegrated','sequentialThreeHulls','rotatedHullImpulse'):
            with self.subTest(key=key):
                invalid=proof.copy();invalid[key]=False
                self.write('build/hullcontactcheck/body-contacts-ok.json',json.dumps(invalid))
                with self.assertRaisesRegex(ValueError,'Missing hull contact proof'):
                    checkpoint.verify_hull_contacts(self.root,self.started)

    def test_hull_contact_gate_rejects_missing_or_stale_scene(self):
        self.hull_contact_fixture()
        frame=self.root/'build/hullcontactcheck/body-contact-frames/three-hull-chain.png'
        os.utime(frame,(self.started-10,self.started-10))
        with self.assertRaisesRegex(ValueError,'Stale evidence'):checkpoint.verify_hull_contacts(self.root,self.started)
        frame.unlink()
        with self.assertRaisesRegex(ValueError,'Missing or empty evidence'):checkpoint.verify_hull_contacts(self.root,self.started)

    def test_hull_contact_gate_rejects_unsynchronized_or_nonfinite_pose(self):
        proof=self.hull_contact_fixture()
        for value in (1.,float('nan'),float('inf'),-1,True):
            with self.subTest(error=value):
                proof['maxPairClientError']=value
                self.write('build/hullcontactcheck/body-contacts-ok.json',json.dumps(proof))
                with self.assertRaisesRegex(ValueError,'Invalid hull contact client error'):
                    checkpoint.verify_hull_contacts(self.root,self.started)

    def test_hull_contact_gate_requires_every_rendered_scene_and_samples(self):
        proof=self.hull_contact_fixture();proof['scenes']=proof['scenes'][:-1]
        self.write('build/hullcontactcheck/body-contacts-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Missing actual rendered'):checkpoint.verify_hull_contacts(self.root,self.started)
        proof=self.hull_contact_fixture();proof['pairClientSamples']=12
        self.write('build/hullcontactcheck/body-contacts-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Missing hull contact samples'):checkpoint.verify_hull_contacts(self.root,self.started)

    def fracture_fixture(self):
        proof=self.hull_contact_fixture()
        proof.update(actualFragments=True,continuousMaterialMotion=True,fastFragmentTickIntegrated=True,
            fragmentNbtRoundTrip=True,minFastFragmentSpeed=64.,fastVisiblePixels=[40,40],maxQuantizedPoseError=0.,precisionWaitSamples=0,fullWorldReload=False,pairClientSamples=767,
            scenes=[dict(scene=name,renderedFrames=90) for name in checkpoint.FRACTURE_SCENES])
        for name in checkpoint.FRACTURE_SCENES:
            self.write(f'build/fracturecheck/body-contact-frames/{name}.png',b'\x89PNG\r\n\x1a\nfixture')
        self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(proof));return proof

    def test_combat_gate_cannot_certify_a_local_edit_only_fracture(self):
        proof=self.fracture_fixture()
        with self.assertRaisesRegex(ValueError,'Missing actual hull combat proof'):checkpoint.verify_body_combat(self.root,self.started)
        proof.update(actualHitscanDamage=True,actualProjectileDamage=True,actualRadialDamage=True)
        self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(proof));checkpoint.verify_body_combat(self.root,self.started)
        for key in ('actualHitscanDamage','actualProjectileDamage','actualRadialDamage'):
            p=proof.copy();p[key]=False;self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(p))
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'Missing actual hull combat proof'):checkpoint.verify_body_combat(self.root,self.started)

    def test_fracture_gate_requires_actual_fragments_and_full_first_tick(self):
        proof=self.fracture_fixture();checkpoint.verify_body_fractures(self.root,self.started)
        for key in ('actualFragments','continuousMaterialMotion','fastFragmentTickIntegrated','fragmentNbtRoundTrip'):
            p=proof.copy();p[key]=False;self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(p))
            with self.subTest(key=key),self.assertRaisesRegex(ValueError,'Missing physical fracture proof'):
                checkpoint.verify_body_fractures(self.root,self.started)

    def test_clipped_or_nonfinite_fragment_velocity_is_not_a_pass(self):
        proof=self.fracture_fixture()
        for value in (0,50,60,513,float('nan'),True):
            p=proof.copy();p['minFastFragmentSpeed']=value;self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(p))
            with self.subTest(speed=value),self.assertRaisesRegex(ValueError,'Missing unclipped fragment velocity'):
                checkpoint.verify_body_fractures(self.root,self.started)

    def test_contact_frames_cannot_replace_new_fracture_frames(self):
        proof=self.fracture_fixture();proof['scenes']=proof['scenes'][:8]
        self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Missing actual rendered hull contact scenes'):
            checkpoint.verify_body_fractures(self.root,self.started)
        self.fracture_fixture();(self.root/'build/fracturecheck/body-contact-frames/fast-fragments.png').unlink()
        with self.assertRaisesRegex(ValueError,'Missing or empty evidence'):
            checkpoint.verify_body_fractures(self.root,self.started)

    def test_render_calls_cannot_replace_visible_fast_fragment_pixels(self):
        proof=self.fracture_fixture()
        for value in (None,[],[40],[40,0],[True,40]):
            p=proof.copy();p['fastVisiblePixels']=value;self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(p))
            with self.subTest(pixels=value),self.assertRaisesRegex(ValueError,'Missing visible fast fragment pixels'):
                checkpoint.verify_body_fractures(self.root,self.started)

    def test_quantized_wait_does_not_relax_the_final_pose_tolerance(self):
        proof=self.fracture_fixture();proof.update(maxQuantizedPoseError=1/4096,precisionWaitSamples=20)
        self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(proof));checkpoint.verify_body_fractures(self.root,self.started)
        proof['maxPairClientError']=1/4096
        self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'Invalid hull contact client error'):checkpoint.verify_body_fractures(self.root,self.started)

    def test_large_or_nonfinite_waiting_error_is_not_relative_packet_precision(self):
        proof=self.fracture_fixture()
        for value in (0.001,float('nan'),True):
            p=proof.copy();p['maxQuantizedPoseError']=value;self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(p))
            with self.subTest(error=value),self.assertRaisesRegex(ValueError,'Invalid relative-packet precision evidence'):
                checkpoint.verify_body_fractures(self.root,self.started)

    def test_entity_nbt_inspection_cannot_claim_full_world_reload(self):
        proof=self.fracture_fixture();proof['fullWorldReload']=True
        self.write('build/fracturecheck/body-contacts-ok.json',json.dumps(proof))
        with self.assertRaisesRegex(ValueError,'must not claim full world reload'):
            checkpoint.verify_body_fractures(self.root,self.started)

    def test_failed_run_still_has_report_without_certifying_jar(self):
        with patch.dict(os.environ, {'VERIFY_OUTCOME': 'failure'}):
            checkpoint.report(self.root)
        report = (self.root / 'build/checkpoint/REPORT.md').read_text()
        self.assertIn('NOT VERIFIED', report)
        self.assertIn('NOT RUN', report)
        self.assertNotIn('## Verified distribution', report)


if __name__ == '__main__':
    unittest.main()

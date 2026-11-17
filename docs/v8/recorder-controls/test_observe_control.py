"""Synthetic filesystem controls only: no Minecraft, Java or measured performance."""
import importlib.util
import json
import os
from pathlib import Path
import struct
import tempfile
import unittest
from unittest import mock
import zlib

SPEC=importlib.util.spec_from_file_location('recorder_observer',Path(__file__).with_name('observe_control.py'))
observer=importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(observer)


def raw_capture(*,frames=3,ticks=16,heaps=3):
    context={name:{'value':None,'reason':'Synthetic missing observer; no real metric'} for name in observer.NULL_CONTEXT}
    context.update(heap_max_bytes={'value':1024,'reason':None},gpu_hardware={'value':'software','reason':None})
    samples={
        'frame_time_ms':{'values':[25.]*frames,'unit':'ms','reason':None},
        'server_tick_ms':{'values':[1.]*ticks if ticks else None,'unit':'ms','reason':None if ticks else 'No observed integrated tick'},
        'heap_used_bytes':{'values':[128]*heaps,'unit':'bytes','reason':None},
    }
    samples.update({name:{'values':None,'unit':'ms' if name=='gpu_frame_ms' else 'count','reason':'Synthetic unavailable counter'} for name in observer.NULL_SERIES})
    return {'schema':'ungravity.benchmark-observations.v1','run_id':observer.RUNS['capacity16'],'scenario':observer.SCENARIOS['capacity16'],
        'window':{'value':{'started_at':'2026-11-17T01:00:00Z','ended_at':'2026-11-17T01:00:02Z','warmup_seconds':1.1},'reason':None},
        'context':context,'samples':samples,'known_issues':['capture_status=truncated_sample_capacity','requested_sample_seconds=30.0; max_samples_per_series=16']}


def png():
    def chunk(kind,data):
        return struct.pack('>I',len(data))+kind+data+struct.pack('>I',zlib.crc32(kind+data)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>IIBBBBB',1,1,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0\0\0\0'))+chunk(b'IEND',b'')


GATE={'frames':observer.SCENES,'actualUseAndSneakKeys':True,'operatorFollowTeleport':False,'fixtureSuppliedJointOrShots':False}
EXIT={'returncode':0,'timed_out':False,'forced_kill':False,'observed_at':'2026-11-17T01:00:10Z'}


class CapacitySchema(unittest.TestCase):
    def test_interleavings_do_not_require_all_three_counts_equal(self):
        for counts in [(3,16,3),(16,4,16),(16,0,16)]:
            with self.subTest(counts=counts):
                value=observer.validate_capacity(raw_capture(frames=counts[0],ticks=counts[1],heaps=counts[2]))
                self.assertEqual(max(value['counts'].values()),16)
                self.assertFalse(value['coverage_is_complete'])

    def test_overflow_or_never_reaching_capacity_is_rejected(self):
        for counts in [(17,16,3),(3,17,3),(3,3,17),(3,3,3)]:
            with self.subTest(counts=counts),self.assertRaises(ValueError):
                observer.validate_capacity(raw_capture(frames=counts[0],ticks=counts[1],heaps=counts[2]))

    def test_target_or_partial_close_is_not_capacity_close(self):
        for status in ['target_window_complete','partial_client_stopping','partial_world_changed']:
            value=raw_capture();value['known_issues'][0]='capture_status='+status
            with self.subTest(status=status),self.assertRaises(ValueError):observer.validate_capacity(value)

    def test_missing_metrics_need_null_and_reason_not_invented_values(self):
        for value in [{'value':None,'reason':None},{'value':0,'reason':None}]:
            raw=raw_capture();raw['context']['cubes_loaded']=value
            with self.subTest(value=value),self.assertRaises(ValueError):observer.validate_capacity(raw)

    def test_nonfinite_timing_bad_heap_or_bad_window_is_rejected(self):
        mutations=[lambda r:r['samples']['frame_time_ms']['values'].__setitem__(0,float('nan')),
            lambda r:r['samples']['heap_used_bytes']['values'].__setitem__(0,1025),
            lambda r:r['window']['value'].__setitem__('ended_at','2026-11-17T01:00:31Z'),
            lambda r:r['window']['value'].__setitem__('started_at','2026-11-17T01:00:00')]
        for change in mutations:
            value=raw_capture();change(value)
            with self.subTest(change=change),self.assertRaises(ValueError):observer.validate_capacity(value)

    def test_cpu_span_sum_is_not_forced_to_equal_utc_window(self):
        raw=raw_capture();result=observer.validate_capacity(raw)
        self.assertNotEqual(result['frame_interval_sum_ms'],result['utc_window_seconds']*1000)
        self.assertNotEqual(result['server_tick_span_sum_ms'],result['frame_interval_sum_ms'])


class Lifecycle(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.base=Path(self.temp.name)
        self.runtime=self.base/'runtime';self.run=self.base/'run';self.out=self.base/'observer'
        for p in [self.runtime,self.run,self.out,self.runtime/'benchmark-observations',self.runtime/'mounted-mechanism-frames']:p.mkdir(exist_ok=True)
        self.capture=self.runtime/'benchmark-observations'/('benchmark-'+observer.RUNS['capacity16']+'.json')
        self.log=self.run/'native.log';self.log.write_bytes(b'')
        self.monitors=[]

    def tearDown(self):
        for value in self.monitors:value.close()
        self.temp.cleanup()

    def monitor(self,case='capacity16'):
        value=observer.Observation(case,self.runtime,self.run,self.out);self.monitors.append(value);return value

    def frames(self,names=observer.SCENES):
        for name in names:(self.runtime/'mounted-mechanism-frames'/(name+'.png')).write_bytes(png())

    def emit(self):
        self.capture.write_text(json.dumps(raw_capture()))
        self.log.write_text('[Render thread/INFO]: BENCHMARK_RECORDER armed\n[Render thread/INFO]: BENCHMARK_RECORDER wrote status=truncated_sample_capacity\n')

    def later(self):
        self.frames()
        with self.log.open('a') as stream:stream.write('[Render thread/INFO]: MOUNTED_CLIENT_USE actual later event\n')

    def test_disabled_actual_observer_sees_zero_files_and_zero_logs(self):
        value=self.monitor('disabled');value.poll();self.frames()
        result=value.finish(EXIT,GATE)
        self.assertEqual(result['status'],'PASS_DISABLED_ZERO_EMISSION')
        self.assertFalse(result['allocation_measurement_performed'])

    def test_disabled_non_json_file_or_armed_line_is_failure(self):
        value=self.monitor('disabled');value.poll()
        unexpected=self.runtime/'benchmark-observations'/'unexpected.tmp';unexpected.write_text('not JSON')
        with self.assertRaises(ValueError):value.poll()
        unexpected.unlink();self.log.write_text('BENCHMARK_RECORDER armed')
        with self.assertRaises(ValueError):value.poll()

    def test_complete_immutable_capture_then_later_frames_and_clean_close(self):
        value=self.monitor();value.poll();self.emit();value.poll();self.later();value.poll()
        result=value.finish(EXIT,GATE)
        self.assertEqual(result['status'],'PASS_CAPACITY16_CLOSED_OUTPUT')
        self.assertEqual(len(result['later_client_frames']),5)
        self.assertFalse(result['timing_counts_are_deterministic'])
        self.assertFalse(result['performance_accepted'])

    def test_all_old_frames_and_new_log_are_not_later_frame_proof(self):
        value=self.monitor();self.frames();self.emit();value.poll()
        with self.log.open('a') as stream:stream.write('[Render thread/INFO]: MOUNTED_CLIENT_USE later log only\n')
        with self.assertRaisesRegex(ValueError,'later completed'):value.finish(EXIT,GATE)

    def test_later_png_without_render_event_is_insufficient(self):
        value=self.monitor();self.emit();value.poll();self.frames()
        with self.assertRaisesRegex(ValueError,'render-thread'):value.finish(EXIT,GATE)

    def test_output_mutation_or_replacement_after_close_is_failure(self):
        value=self.monitor();self.emit();value.poll()
        raw=self.capture.read_bytes();self.capture.write_bytes(raw+b'\n')
        with self.assertRaisesRegex(ValueError,'changed/replaced'):value.poll()

    def test_duplicate_output_and_disappearing_output_are_failures(self):
        value=self.monitor();self.emit();value.poll()
        duplicate=self.capture.with_name('duplicate.json');duplicate.write_text('{}')
        with self.assertRaisesRegex(ValueError,'Duplicate'):value.poll()
        duplicate.unlink();self.capture.unlink()
        with self.assertRaisesRegex(ValueError,'disappeared'):value.poll()

    def test_incomplete_json_or_png_never_counts_as_lifecycle_event(self):
        value=self.monitor();self.capture.write_text('{');value.poll();self.assertIsNone(value.first)
        self.emit();value.poll()
        partial=self.runtime/'mounted-mechanism-frames'/'laser-fire.png';partial.write_bytes(png()[:-2])
        value.poll();self.assertFalse(value.later_frames)
        partial.write_bytes(png());value.poll();self.assertIn('laser-fire.png',value.later_frames)

    def test_duplicate_emission_or_native_timeout_cannot_pass(self):
        value=self.monitor();self.emit();value.poll();self.later()
        with self.log.open('a') as stream:stream.write('BENCHMARK_RECORDER wrote again\n')
        with self.assertRaisesRegex(ValueError,'exactly one'):value.finish(EXIT,GATE)
        with self.assertRaisesRegex(ValueError,'cleanly'):value.finish({**EXIT,'timed_out':True},GATE)

    def test_source_failure_or_substituted_gate_is_not_success(self):
        value=self.monitor();self.emit();value.poll();self.later()
        with self.assertRaisesRegex(ValueError,'gate incomplete'):value.finish(EXIT,{**GATE,'fixtureSuppliedJointOrShots':True})
        with self.log.open('a') as stream:stream.write('BENCHMARK_RECORDER FAIL duplicate file\n')
        with self.assertRaisesRegex(ValueError,'Producer reported'):value.poll()


class PngControls(unittest.TestCase):
    def test_complete_image_only_not_file_touch_or_partial_crc(self):
        self.assertTrue(observer.png_complete(png()))
        for raw in [b'',b'not image',png()[:-1],png()[:-5]+b'00000',png()+b'junk']:
            with self.subTest(size=len(raw)):self.assertFalse(observer.png_complete(raw))


class SnapshotControls(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.base=Path(self.temp.name)
        self.runtime=self.base/'runtime';self.runtime.mkdir();(self.runtime/'mods').mkdir()
        self.run=self.base/'run';self.run.mkdir();self.out=self.base/'observer'
        self.executor=b'synthetic never-executed executor'
        (self.run/'runner.snapshot.py').write_bytes(self.executor)
        self.jar=b'synthetic never-executed game'
        (self.runtime/'mods'/'synthetic.jar').write_bytes(self.jar)
        self.args=['NEVER_EXECUTE','-Ddrmd.gate.mountedMechanisms=true','--gameDir',str(self.runtime)]
        self.launch=self.runtime/'launch.json';self.launch.write_text(json.dumps(self.args))
        (self.run/'launch.snapshot.json').write_bytes(self.launch.read_bytes())
        self.manifest=self.run/'manifest.json'
        self.data={'runner_sha256':observer.sha(self.executor),'launch_sha256':observer.sha(self.launch.read_bytes()),
            'original_launch_path':str(self.launch),'original_launch_sha256':observer.sha(self.launch.read_bytes()),
            'runtime':str(self.runtime),'contract':{'tested_source':observer.SOURCE,'case':'disabled'}}
        self.manifest.write_text(json.dumps(self.data))
        self.patch=mock.patch.multiple(observer,EXECUTOR=observer.sha(self.executor),JARS={'synthetic.jar':observer.sha(self.jar)})
        self.patch.start()

    def tearDown(self):self.patch.stop();self.temp.cleanup()

    def test_actual_snapshot_copied_and_bounded_inputs_bound_without_launch(self):
        observer.prepare(self.manifest,'disabled',self.out)
        config_path=self.out/'observer-inputs.json';original=config_path.read_bytes();config=json.loads(original)
        code_path=self.out/'observer.snapshot.py'
        self.assertEqual(observer.sha(code_path.read_bytes()),config['observer_sha256'])
        observer.verify_observer_bytes(config_path,observer.sha(original),code_path,config['observer_sha256'])
        config_path.chmod(0o600);config_path.write_bytes(original+b'\n')
        with self.assertRaisesRegex(ValueError,'configuration changed'):
            observer.verify_observer_bytes(config_path,observer.sha(original),code_path,config['observer_sha256'])
        config_path.write_bytes(original);code_path.chmod(0o600);code_path.write_text('changed synthetic observer copy')
        with self.assertRaisesRegex(ValueError,'snapshot changed'):
            observer.verify_observer_bytes(config_path,observer.sha(original),code_path,config['observer_sha256'])
        self.assertFalse((self.run/'native.log').exists())
        self.assertIn('synthetic.jar',config['inputs']['staged_mods_not_loaded_registry'])

    def test_unsupported_flag_injection_is_rejected(self):
        for name in ('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS'):
            with self.subTest(name=name),mock.patch.dict(os.environ,{name:'-Ddrmd.gate.benchmark=true'}),self.assertRaises(ValueError):
                observer.validate_inputs(self.manifest,'disabled')

    def test_late_snapshot_or_changed_actual_input_is_rejected(self):
        (self.run/'native.log').write_text('already started')
        with self.assertRaisesRegex(ValueError,'before actual'):observer.prepare(self.manifest,'disabled',self.out)
        (self.run/'native.log').unlink();self.launch.write_text('changed original')
        with self.assertRaisesRegex(ValueError,'Original launch'):observer.validate_inputs(self.manifest,'disabled')


if __name__=='__main__':unittest.main()

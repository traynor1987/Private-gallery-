"""Public synthetic, test-only design constraints; no production or AEAD claim."""
import importlib.util
import pathlib
import json
import hashlib
import unittest

ROOT = pathlib.Path(__file__).parent
spec = importlib.util.spec_from_file_location('contracts', ROOT/'reference'/'contracts.py')
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)

class DesignContracts(unittest.TestCase):
    def test_reference_is_exactly66_bytes(self):
        ref = c.reference(7, bytes(range(1,17)), 1, 400, bytes(range(32)))
        self.assertEqual(len(ref),66)
        self.assertEqual(c.parse_reference(ref), (7, bytes(range(1,17)),1,400,bytes(range(32))))
        for bad in [ref[:-1],ref+b'X',b'\0\0'+ref[2:],ref[:18]+b'\0'*8+ref[26:]]:
            with self.assertRaises(ValueError): c.parse_reference(bad)
    def test_context_is_not_authority(self):
        with self.assertRaises(ValueError): c.authorize({'primary':True,'hidden':False,'fresh':True})
    def test_unknown_and_duplicate_closure(self):
        a=(7,b'A'*16,1)
        with self.assertRaises(ValueError): c.closure([a,a])
        with self.assertRaises(ValueError): c.closure([(2,b'A'*16,1)])
    def test_ledger_exact82_and_limits(self):
        data=c.ledger(8192,536961024,8192,1,b'H'*32,b'A'*16)
        self.assertEqual(len(data),82)
        self.assertEqual(c.parse_ledger(data)[0],8192)
        for bad in [data[:-1],data+b'X',b'\0\1'+data[2:]]:
            with self.assertRaises(ValueError): c.parse_ledger(bad)
        with self.assertRaises(ValueError): c.ledger(1,2**32+1,0,1,b'H'*32,b'A'*16)
    def test_video_independent_charge(self):
        self.assertEqual(c.video_charge(8*2**30),(8192,536961024))
        self.assertEqual(c.video_charge(0),(0,0))
        self.assertEqual(c.video_charge(2**20+1),(2,65559))
    def test_query_preflight_no_partial_full_sweep(self):
        self.assertEqual(c.preflight([2**20-8192,4],[8192,1]),[2**20,5])
        old=[2**20-8191,4]
        with self.assertRaises(ValueError): c.preflight(old,[8192,1])
        self.assertEqual(old,[2**20-8191,4])
    def test_no_refund_on_crash(self):
        committed=c.preflight([10,20],[8,1])
        self.assertEqual(committed,[18,21])
        self.assertEqual(c.abandon(committed),committed)
    def test_unknown_length_two_pass(self):
        c.two_pass(b'abc',b'abc',3,1,1)
        for args in [(b'abc',b'abd',3,1,1),(b'abc',b'abcX',3,1,1),(b'a',b'a',0,1,1),(b'a',b'a',2,1201,1),(b'a',b'a',2,1,31)]:
            with self.assertRaises(ValueError): c.two_pass(*args)
    def test_metadata_changes_do_not_supersede_raw_bytes(self):
        old=c.item()
        new=dict(old,name='changed',crop=(0.1,0.1,0.9,0.9),meta=2)
        self.assertTrue(c.cleanup_predicate(old,new))
        for key,value in [('state','trash'),('payload',b'X'*32),('origin',2),('restrictions',0),('generation',2)]:
            self.assertFalse(c.cleanup_predicate(old,dict(new,**{key:value})))
    def test_shared_dependency_retention(self):
        self.assertEqual(c.unreferenced({'index','receipt'},{'t1':{'index'},'t2':{'index','receipt'}}),set())
        self.assertEqual(c.unreferenced({'index','receipt'},{'t1':{'index'}}),{'receipt'})
    def test_release_not_before_pair_ack(self):
        for ack in [False,True]:
            self.assertEqual(c.release_allowed('ACTIVE_HOLD',ack),False)
        self.assertFalse(c.release_allowed('RESTORED',False))
        self.assertTrue(c.release_allowed('RELEASE_ACK',True))
    def test_hold_legal_edges_and_no_immediate_delete(self):
        for edge in [(1,2),(2,3),(3,4),(4,5),(5,6),(6,7),(5,8),(8,9),(7,12)]: c.transition(*edge)
        for edge in [(2,5),(3,9),(5,9),(6,12),(12,5)]:
            with self.assertRaises(ValueError): c.transition(*edge)
    def test_backup_refuses_unresolved_source_ownership(self):
        for state in [1,2,3,4,5,6,8]: self.assertFalse(c.backup_allowed([state]))
        for state in [7,9,10,11,12]: self.assertTrue(c.backup_allowed([state]))
    def test_restore_merges_later_writes(self):
        self.assertEqual(c.restore({'later':'new'},'held','old'),{'later':'new','held':'old'})
        with self.assertRaises(ValueError): c.restore({'held':'replacement'},'held','old')
    def test_exception_does_not_unselect_winner(self):
        self.assertEqual(c.restart('next',{'prior','next'}),'next')
        with self.assertRaises(ValueError): c.restart('unknown',{'prior','next'})
    def test_lock_edges_form_dag_and_reverse_fails(self):
        c.lock_path([0,2,3,4,6,7,8,9,10])
        c.lock_path([5,8])
        for path in [[3,0],[6,4],[8,5],[7,6]]:
            with self.assertRaises(ValueError): c.lock_path(path)
    def test_cleanup_callbacks_outside_all_locks(self):
        c.cleanup([])
        for held in [[3],[4],[6],[8]]:
            with self.assertRaises(ValueError): c.cleanup(held)
    def test_cleanup_barrier_not_timeout_success(self):
        self.assertFalse(c.admit_after_cleanup(0,False,1))
        self.assertFalse(c.admit_after_cleanup(0,True,0))
        self.assertTrue(c.admit_after_cleanup(0,False,0))
    def test_active_slot_proof_not_just_unwrap(self):
        p=c.proof()
        self.assertTrue(c.proof_valid(p))
        for key in ['active','catalog_hash','anchor_hash','selected','confirmed','slot_hash','ledger','cleanup']:
            with self.assertRaises(ValueError): c.proof_valid(dict(p,**{key:False}))
    def test_proof_narrow_foreign_stale_or_consumed(self):
        p=c.proof()
        c.consume(p,'restore','t1',1)
        with self.assertRaises(ValueError): c.consume(p,'restore','t1',1)
        for action,transfer,revision in [('cleanup','t1',1),('media','t1',1),('restore','t2',1),('restore','t1',2)]:
            with self.assertRaises(ValueError): c.consume(c.proof(),action,transfer,revision)
        with self.assertRaises(ValueError): c.consume(dict(c.proof(),fresh=False),'restore','t1',1)
    def test_restricted_restore_does_not_require_video_or_index(self):
        self.assertTrue(c.proof_valid(dict(c.proof(),media_index=False,video_budget=False)))
    def test_secret_and_key_not_persisted_in_proof(self):
        p=c.proof()
        self.assertNotIn('master',p)
        self.assertNotIn('secret',p)
    def test_crash_restore_never_unlinks_held_payload(self):
        for winner in ['prior','next']:
            self.assertTrue(c.restore_crash(winner)['payload_retained'])
    def test_receipt_dependency_dag(self):
        c.dag({'index':set(),'receipt':{'index'},'journal':{'receipt'},'descriptor':{'index','receipt','journal'}})
        with self.assertRaises(ValueError): c.dag({'index':{'receipt'},'receipt':{'index'}})

    def test_published_serialization_vectors(self):
        vectors=json.loads((ROOT/'design-vectors.json').read_text())
        for name,size in [('hidden_bootstrap_120',120),('primary_bootstrap_102',102),('empty_I2_21',21),('reference_66',66),('usage_82',82)]:
            self.assertEqual(len(bytes.fromhex(vectors[name])),size)
        c.parse_reference(bytes.fromhex(vectors['reference_66']))
        c.parse_ledger(bytes.fromhex(vectors['usage_82']))
        h=bytes.fromhex(vectors['hidden_bootstrap_120'])
        self.assertEqual(h[:10],b'PGDOMB02\0\2')
        self.assertEqual(h[100:104],b'\0'*4)
        self.assertEqual(h[104:],b'\6'*16)

    def test_merge_frozen_vector(self):
        vectors=json.loads((ROOT/'design-vectors.json').read_text())
        raw=c.merge_bytes(bytes.fromhex(vectors['merge_fixture_item_body']),[('c1','Collection',123,0,'item1',456)],None)
        self.assertEqual(raw.hex(),vectors['merge_M1'])
        self.assertEqual(hashlib.sha256(raw).hexdigest(),vectors['merge_M1_sha256'])

    def test_merge_projection_rejects_ambiguity(self):
        for rows in [[('b','B',1,0,None,None),('a','A',1,0,None,None)],[('a','A',1,0,None,None)]*2,[('a','A',1,2,None,None)]]:
            with self.assertRaises(ValueError): c.merge_bytes(b'item',rows,None)
        with self.assertRaises(ValueError): c.merge_bytes(b'x'*65536,[],None)

    def test_merge_changes_only_bound_relationships(self):
        row=('c1','Collection',123,0,'item1',456)
        original=c.merge_bytes(b'item',[row],None)
        self.assertNotEqual(original,c.merge_bytes(b'item',[row[:-1]+(457,)],None))
        self.assertNotEqual(original,c.merge_bytes(b'item',[row],'otherFavourite'))
        self.assertEqual(original,c.merge_bytes(b'item',[row],None))

    def test_depth6_only_owned_attempt_file(self):
        name='0002-'+'01'*16+'-'+'0000000000000001'
        valid='transactions/media/attempts/'+'02'*16+'/files/'+name
        c.legal_attempt_path(valid,{name})
        for invalid in [valid+'/extra',valid.replace('/files/','/unowned/'),valid[:-1]+'2']:
            with self.assertRaises(ValueError): c.legal_attempt_path(invalid,{name})

    def test_partial_ledger_charge_never_mints_restart_lease(self):
        for boundary in [0,1]:
            charges,lease=c.charge_pair([10,20],[1,8],boundary)
            self.assertFalse(lease)
            self.assertEqual(charges[0],11)
            self.assertEqual(c.abandon(charges),charges)
        self.assertEqual(c.charge_pair([10,20],[1,8]),([11,28],True))

if __name__=='__main__': unittest.main()

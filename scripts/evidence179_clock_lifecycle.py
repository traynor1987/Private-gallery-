"""Replay required actual Kotlin lifecycle controls; separate from route calibration."""
import base64
from pathlib import Path
import re
try:
    import evidence179_clock as clock
except ModuleNotFoundError:
    from scripts import evidence179_clock as clock

NAMES=['competing_samples','cancel_before_claim','cancel_after_claim','timeout_before_read','completion_deadline_race','late_stale_request','cleanup_throws','cancelled_caller']
POISON=dict(zip(NAMES,['Rejected clock command: java.lang.IllegalStateException','Clock rendezvous cancelled/expired','Clock closed without sample','Clock sample deadline expired','Clock sample deadline expired','Clock rendezvous cancelled/expired','Clock closed without sample','Clock closed without sample']))
FILE='evidence179-clock-lifecycle.json'
require=clock.require
integer=clock.strict_int


def script_for(argv):
    require(type(argv) is list and all(type(x) is str and re.fullmatch(r'[A-Za-z0-9_./:#=+\-]+',x) is not None for x in argv),'Unsafe lifecycle shell argument')
    return ' '.join("'"+x+"'" for x in argv)+"\ne179_status=$?\nprintf '\\nE179_CLOCK_STATUS=%s\\n' \"$e179_status\"\nexit \"$e179_status\"\n"


def raw_bytes(value):
    require(type(value) is str and re.fullmatch(r'[A-Za-z0-9_-]*',value) is not None and len(value)<=21846,'Invalid raw lifecycle stream')
    result=base64.urlsafe_b64decode(value+'='*((-len(value))%4))
    require(len(result)<=16384 and base64.urlsafe_b64encode(result).decode().rstrip('=')==value,'Noncanonical raw lifecycle stream')
    return result


def parse(raw):
    require(type(raw) is str and len(raw.encode())<=16384,'Invalid lifecycle raw reply')
    match=re.fullmatch(r'Broadcasting: Intent \{[^\r\n]*}\r?\nBroadcast completed: result=(0|178|179)(?:, data="([A-Za-z0-9_-]+)")?\r?\n\r?\nE179_CLOCK_STATUS=0\r?\n',raw)
    require(match is not None,'Failed/malformed lifecycle shell broadcast')
    code=int(match[1]);encoded=match[2]
    require((code==0)==(encoded is None),'Missing/extra lifecycle result data')
    if encoded is None:return code,None
    decoded=base64.urlsafe_b64decode(encoded+'='*((-len(encoded))%4))
    require(base64.urlsafe_b64encode(decoded).decode().rstrip('=')==encoded,'Noncanonical lifecycle payload')
    value=clock.decode(decoded);require(type(value) is dict,'Invalid lifecycle payload')
    return code,value


def request(row,event,index,last):
    argv=event['argv'];require(type(argv) is list and all(type(x) is str for x in argv),'Invalid lifecycle argv')
    prefix=['timeout','3','am','broadcast','--user',str(row['identity']['user']),'--receiver-foreground','-a','uk.co.traynor.privategallery.EVIDENCE179_CLOCK_'+row['run_nonce'],'-p',clock.PACKAGE]
    require(argv[:11]==prefix and (len(argv)-11)%3==0,'Wrong lifecycle broadcast targeting')
    extras={}
    for i in range(11,len(argv),3):
        require(argv[i]=='--es' and argv[i+1] not in extras,'Malformed/duplicate lifecycle extras');extras[argv[i+1]]=argv[i+2]
    op='STATE' if index in (0,last) else 'SAMPLE'
    expected=dict(op=op,run_nonce=row['run_nonce'],source_sha=row['source_sha'],case='vpn_loss',method=clock.METHODS['vpn_loss'],phase='loss')
    if op=='SAMPLE':
        challenge=extras.get('request_nonce');token=extras.get('arm_token')
        require(type(challenge) is str and re.fullmatch(r'[a-f0-9]{32}',challenge) is not None,'Missing lifecycle challenge')
        require(type(token) is str and re.fullmatch(r'[a-f0-9]{32}',token) is not None,'Missing lifecycle arm token')
        stale=row['name']=='late_stale_request' and index==2
        require((token!=row['arm_token']) if stale else (token==row['arm_token']),'Wrong stale/current lifecycle arm')
        expected.update(nonce=row['nonce'],arm_token=token,arm_sequence='1',request_nonce=challenge)
    require(extras==expected,'Foreign lifecycle request identity')
    return extras


def check(value,positive,source):
    require(set(value)=={'schema','kind','source_sha','case','method','classname','parent_route_nonce','identity','scenarios'},'Invalid lifecycle artifact schema')
    require(type(value['schema']) is int and value['schema']==1 and value['kind']=='android_clock_lifecycle_controls','Wrong lifecycle artifact kind')
    require(value['source_sha']==source and value['case']=='vpn_revoke' and value['method']==clock.METHODS['vpn_revoke'] and value['classname']==clock.CLASS and value['parent_route_nonce']==positive['nonce'],'Foreign lifecycle source/case/route')
    identity=value['identity'];require(set(identity)=={'pid','uid','user','package','boot_id','instrumentation'},'Invalid lifecycle process identity')
    require(all(identity[k]==positive['clock'][k] for k in identity),'Lifecycle/positive process identity differs')
    integer(identity['pid']);integer(identity['uid']);integer(identity['user'],0)
    require(identity['uid']//100000==identity['user'] and identity['uid']==positive['uid'] and identity['package']==clock.PACKAGE and identity['instrumentation']==clock.RUNNER,'Unowned lifecycle process')
    clock.boot_uuid(identity['boot_id'].encode())
    scenarios=value['scenarios'];require(type(scenarios) is list and [x['name'] for x in scenarios]==NAMES,'Missing/duplicate/unordered lifecycle scenarios')
    nonces={positive['nonce'],positive['clock']['run_nonce']};challenges=set();tokens=set()
    for item in scenarios:
        require(set(item)=={'name','run_nonce','nonce','arm_token','events','observation','close_succeeded','post_close_action_error','action_elapsed_ns','primary_error','caller_cancelled','close_error_type','close_error_message','suppressed_close_errors','deadline_target_elapsed_ns','deadline_release_elapsed_ns'},'Incomplete lifecycle scenario')
        for key in ('run_nonce','nonce'):
            require(type(item[key]) is str and re.fullmatch(r'[a-f0-9]{32}',item[key]) is not None and item[key] not in nonces,'Reused/foreign lifecycle nonce');nonces.add(item[key])
        require(type(item['arm_token']) is str and re.fullmatch(r'[a-f0-9]{32}',item['arm_token']) is not None and item['arm_token'] not in tokens,'Reused lifecycle arm');tokens.add(item['arm_token'])
        name=item['name'];row=dict(item,identity=identity,source_sha=source)
        events=item['events'];expected_count=4 if name in ('competing_samples','late_stale_request') else 3
        require(type(events) is list and len(events)==expected_count,'Missing/duplicate lifecycle request inventory')
        replies=[];sample_events=[];failed_pending=None;successful_event=None;rejected_events=[]
        for i,event in enumerate(events):
            require(set(event)=={'argv','entrypoint','script','stdout_b64','stderr_b64','raw','sent_elapsed_ns','received_elapsed_ns'},'Malformed lifecycle raw event')
            require(event['entrypoint']=='sh -s' and event['script']==script_for(event['argv']),'Shell entrypoint/script/argv differ')
            require(type(event['raw']) is str and raw_bytes(event['stdout_b64'])==event['raw'].encode('utf-8') and raw_bytes(event['stderr_b64'])==b'','Raw shell streams differ or stderr is nonempty')
            sent=integer(event['sent_elapsed_ns']);received=integer(event['received_elapsed_ns'])
            require(0<=received-sent<=4_000_000_000,'Unbounded/reversed lifecycle command')
            if i:require(sent>=events[i-1]['sent_elapsed_ns'],'Reordered lifecycle requests')
            extras=request(row,event,i,len(events)-1);code,payload=parse(event['raw']);replies.append(code)
            if i==len(events)-1:
                require(code==0 and sent>=max(x['received_elapsed_ns'] for x in events[:-1]),'Receiver not retired before actual OS probe');continue
            if i==0:require(code==179 and payload.get('kind')=='ready','No actual pending phase readiness')
            else:
                require(extras['request_nonce'] not in challenges,'Reused lifecycle challenge');challenges.add(extras['request_nonce']);sample_events.append(event)
                require(sent>=events[0]['received_elapsed_ns'],'SAMPLE before readiness reply')
            if code==178:
                require(set(payload)=={'error'} and payload['error'] in ('clock_failed','clock_rejected'),'Unexpected lifecycle error response')
                if payload['error']=='clock_failed':require(failed_pending is None,'Multiple accepted PendingResults');failed_pending=event
                else:rejected_events.append(event)
            else:
                require(code==179,'Unexpected lifecycle broadcast result')
                expected_identity=dict(source_sha=source,run_nonce=item['run_nonce'],case='vpn_loss',**identity)
                clock.validate_payload(payload,expected_identity,'ready' if i==0 else 'sample')
                require(payload['nonce']==item['nonce'] and payload['phase']=='loss' and payload['arm_token']==item['arm_token'] and payload['arm_sequence']==1 and payload['pid']==identity['pid'] and payload['uid']==identity['uid'],'Foreign lifecycle response binding')
                if i:
                    require(successful_event is None,'Multiple successful lifecycle samples');successful_event=event
                    require(payload['request_nonce']==extras['request_nonce'] and payload['sample_elapsed_ns']==item['action_elapsed_ns'] and sent<=payload['sample_elapsed_ns']<=received,'Sample does not bind actual one-shot read')
        won=name=='completion_deadline_race' and replies[1]==179
        duplicate=name=='competing_samples';had_success=won or duplicate
        if duplicate:require(replies[0]==179 and replies[-1]==0 and sorted(replies[1:-1])==[178,179] and len(rejected_events)==1,'Duplicate must follow one actual completed sample')
        else:require(replies==([179,179,0] if won else [179]+[178]*(expected_count-2)+[0]),'Wrong lifecycle outcomes')
        accepted=0 if name in ('cancel_before_claim','late_stale_request') else 1
        require((failed_pending is not None)==(accepted==1 and not had_success),'PendingResult/raw result mismatch')
        obs=item['observation'];require(set(obs)=={'registered','thread_alive','owner_thread_id','poison','poison_elapsed_ns','poison_order','claims'} and obs['registered'] is False and obs['thread_alive'] is False,'Clock receiver/thread not retired')
        owner=integer(obs['owner_thread_id']);require(type(obs['claims']) is list and len(obs['claims'])==1,'Wrong lifecycle claim inventory')
        claim=obs['claims'][0];require(set(claim)=={'phase','accepted','finish_count','finish_thread_id','pending','sample_elapsed_ns','claimed_elapsed_ns','sample_order','finish_order','finish_elapsed_ns'} and claim['phase']=='loss' and claim['pending'] is False,'Invalid final claim observation')
        require(integer(claim['accepted'],0,1)==integer(claim['finish_count'],0,1)==accepted and integer(claim['finish_thread_id'],0)==(owner if accepted else 0),'PendingResult did not finish once on owner thread')
        claimed=integer(claim['claimed_elapsed_ns'],0)
        if accepted:
            accepted_event=successful_event if had_success else failed_pending
            require(accepted_event['sent_elapsed_ns']<=claimed<=accepted_event['received_elapsed_ns'],'Claim outside actual request')
        else:require(claimed==0,'Unaccepted request has claim counter')
        action=integer(item['action_elapsed_ns'],0);require(integer(claim['sample_elapsed_ns'],0)==action,'Published sample disagrees with actual read')
        require((action>0)==had_success and (not had_success or 0<=action-claimed<=1_000_000_000),'Successful sample after timeout/poison')
        poison_at=integer(obs['poison_elapsed_ns'],0);poison_order=integer(obs['poison_order'],0)
        sample_order=integer(claim['sample_order'],0);finish_order=integer(claim['finish_order'],0);finished=integer(claim['finish_elapsed_ns'],0)
        require(sample_order==(1 if had_success else 0) and finish_order==(2 if accepted else 0) and poison_order==(0 if won else 3 if duplicate else 1),'Sample/finish/poison order is inconsistent')
        if accepted:
            require(claimed<=finished<=events[-1]['sent_elapsed_ns'],'Finish outside accepted/retired lifetime')
            if had_success:require(action<=finished,'Finish before actual sample')
        else:require(finished==0,'Unaccepted request has completion timestamp')
        if won:require(poison_at==0,'Healthy deadline winner was poisoned')
        elif duplicate:
            rejected=rejected_events[0]
            require(finished<=poison_at and rejected['sent_elapsed_ns']<=poison_at<=rejected['received_elapsed_ns'],'Sample/finish must precede actual duplicate poison')
        else:
            require(0<poison_at<=events[-1]['sent_elapsed_ns'],'Missing/out-of-lifetime poison')
            if accepted:require(claimed<=poison_at<=finished,'Timeout/cancel poison did not precede pending finish')
        target=integer(item['deadline_target_elapsed_ns'],0);released=integer(item['deadline_release_elapsed_ns'],0)
        if name=='completion_deadline_race':
            require(target==claimed+1_000_000_000 and -1_000_000<=released-target<=50_000_000,'Completion did not race actual claim deadline')
            if won:require(released<=action,'Action read predates deadline latch release')
        else:require(target==released==0,'Foreign deadline observation')
        if name in ('timeout_before_read','completion_deadline_race') and not won:require(failed_pending['received_elapsed_ns']>=claimed+1_000_000_000,'Timeout reply predates real deadline')
        require(obs['poison']==(None if won else POISON[name]) and item['close_succeeded'] is won,'Poisoned/late successful final receipt')
        require(item['post_close_action_error']==('Clock is closing' if won else POISON[name]),'Later action did not reject for poison/closure')
        require(item['caller_cancelled'] is (name in ('cancel_before_claim','cancel_after_claim','cancelled_caller')),'Cancellation not exercised')
        require(item['primary_error']==('injected-route-cleanup' if name=='cleanup_throws' else 'cancelled' if name in ('cancel_after_claim','cancelled_caller') else ''),'Cleanup/cancellation primary failure lost')
        injected=name=='cleanup_throws';cancelled=name in ('cancel_after_claim','cancelled_caller')
        expected_suppressed=[dict(type='java.lang.IllegalStateException',message=POISON[name])] if injected or cancelled else []
        require(item['suppressed_close_errors']==expected_suppressed,'Suppressed teardown failure missing/changed')
        if won:require(item['close_error_type']==item['close_error_message']=='','Healthy deadline winner failed close')
        elif cancelled:
            require(item['close_error_type'] in ('kotlinx.coroutines.JobCancellationException','java.util.concurrent.CancellationException') and type(item['close_error_message']) is str and 0<len(item['close_error_message'])<=1024,'Original caller cancellation not preserved')
        else:require(item['close_error_type']=='java.lang.IllegalStateException' and item['close_error_message']==('injected-route-cleanup' if injected else POISON[name]),'Unexpected/swallowed teardown error')
        if name=='competing_samples':require(max(x['sent_elapsed_ns'] for x in sample_events)<=min(x['received_elapsed_ns'] for x in sample_events),'Competing sends did not overlap')


def verify(root,positive,source):
    root=Path(root);path=root/FILE
    copies=list((root/'android-output').rglob(FILE))
    require(len(copies)==1 and copies[0].relative_to(root/'android-output').parts[0]=='vpn_revoke','Missing/duplicate/foreign Android lifecycle receipt')
    require(path.is_file() and not path.is_symlink() and not copies[0].is_symlink(),'Missing lifecycle artifact')
    raw=path.read_bytes();require(raw==copies[0].read_bytes(),'Android lifecycle receipt bytes differ')
    check(clock.decode(raw),positive,source)
    return [path,copies[0]]

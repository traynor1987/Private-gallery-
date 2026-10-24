import uk.co.traynor.privategallery.core.ui.PanicTouchGate;
public final class PanicTouchGateChecks {
 static void eq(int expected,int actual){if(expected!=actual)throw new AssertionError("expected "+expected+", got "+actual);}
 public static void main(String[]args){
  PanicTouchGate gate=new PanicTouchGate();
  eq(0,gate.dispatch(0,1,true,false));eq(0,gate.dispatch(5,2,true,false));eq(1,gate.dispatch(5,3,true,false));
  eq(2,gate.dispatch(2,3,true,false));eq(2,gate.dispatch(6,3,true,false));eq(2,gate.dispatch(5,3,true,false));eq(2,gate.dispatch(1,1,true,false));
  eq(0,gate.dispatch(0,1,false,false));eq(0,gate.dispatch(5,3,false,false));eq(0,gate.dispatch(1,1,false,false));
  eq(0,gate.dispatch(0,1,true,true));eq(0,gate.dispatch(5,3,true,true));eq(0,gate.dispatch(3,1,true,true));
  eq(0,gate.dispatch(0,1,true,false));eq(1,gate.dispatch(5,3,true,false));eq(2,gate.dispatch(3,3,false,false));
  eq(0,gate.dispatch(0,1,true,false));eq(0,gate.dispatch(2,2,true,false));
  System.out.println("18 actual gesture-gate checks passed");
 }
}

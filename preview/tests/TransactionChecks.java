import uk.co.traynor.novaos.preview.LayoutTransaction;
public class TransactionChecks {
 public static void main(String[]args){int[] state={1,2};boolean ok=LayoutTransaction.apply(()->{state[0]=3;return true;},()->false,()->{state[0]=1;return true;},()->{state[1]=2;return true;});if(ok||state[0]!=1||state[1]!=2)throw new AssertionError("Partial restore must roll back both stores");if(!LayoutTransaction.apply(()->true,()->true,()->false,()->false))throw new AssertionError("Success should not roll back");boolean threw=false;try{LayoutTransaction.apply(()->false,()->true,()->false,()->true);}catch(IllegalStateException expected){threw=true;}if(!threw)throw new AssertionError("Failed rollback must surface");System.out.println("3 layout transaction/rollback checks passed");}
}

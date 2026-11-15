import java.util.*;
import org.eclipse.swt.internal.mobile.MobileTableSelection;
public class MobileTableSelectionTest {
 static long checks;
 static void require(boolean c,String m){ checks++;if(!c)throw new AssertionError(m); }
 static void compare(MobileTableSelection x,BitSet oracle,int n){
  require(x.count()==oracle.cardinality(),"count mismatch");
  require(x.first()==oracle.nextSetBit(0),"first mismatch");
  int[] indices=x.indices();int i=0;
  for(int bit=oracle.nextSetBit(0);bit>=0;bit=oracle.nextSetBit(bit+1))require(indices[i++]==bit,"list mismatch");
  require(i==indices.length,"array length mismatch");
  for(int j=0;j<n;j++)require(x.contains(j)==oracle.get(j),"selection mismatch @"+j);
 }
 public static void main(String[] args){
  MobileTableSelection x=new MobileTableSelection();BitSet o=new BitSet();Random rand=new Random(10257);int n=200;
  for(int step=0;step<10000;step++){
   if(n<2){x.clear();o.clear();n=200;}
   int a=rand.nextInt(n),b=a+rand.nextInt(n-a);
   switch(rand.nextInt(6)){
    case 0 ->{x.select(a,b);o.set(a,b+1);}
    case 1 ->{x.deselect(a,b);o.clear(a,b+1);}
    case 2 ->{x.toggle(a);o.flip(a);}
    case 3 ->{x.selectAll(n);o.set(0,n);}
    case 4 ->{x.clear();o.clear();}
    case 5 ->{
       if(rand.nextBoolean()){
          x.insertRow(a);BitSet next=new BitSet();
          for(int i=o.nextSetBit(0);i>=0;i=o.nextSetBit(i+1))next.set(i>=a?i+1:i);
          o=next;n++;
       }else if(n>3){
          x.removeRows(a,b);BitSet next=new BitSet();
          for(int i=o.nextSetBit(0);i>=0;i=o.nextSetBit(i+1)){
           if(i<a)next.set(i);else if(i>b)next.set(i-(b-a+1));
          }
          o=next;n-=b-a+1;
       }
    }
   }
   compare(x,o,n);
  }
  x.selectAll(1_000_000_000);
  require(x.intervalCount()==1 && x.count()==1_000_000_000,"billion-row selectAll should occupy one interval");
  x.deselect(10,999_999_998);
  require(x.intervalCount()==2 && x.count()==11,"billion-row middle deselect");
  x.insertRow(2);
  require(x.count()==11 && !x.contains(2) && x.contains(3),"insert splits selected run");
  x.removeRows(0,3);
  require(x.count()==8 && x.first()==0,"remove merges selected intervals");
  System.out.println("PASS "+checks+" sparse Table selection range assertions");
 }
}

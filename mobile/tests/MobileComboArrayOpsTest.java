/*******************************************************************************
 * Copyright (c) 2026 Synexia, Hitesh Soliwal (hsoliwal@gmail.com) and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0, or (at your option) the Eclipse
 * Public License 2.0, which are available at
 * https://www.apache.org/licenses/LICENSE-2.0 and
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 *
 * Contributors:
 *     Hitesh Soliwal (Synexia) - initial API and implementation
 *******************************************************************************/
import java.util.Arrays;
import java.util.Random;
import java.util.ArrayList;
import org.eclipse.swt.internal.mobile.MobileComboArrayOps;
public class MobileComboArrayOpsTest {
    static long checks;
    static void eq(Object a,Object b) {checks++;if(!java.util.Objects.equals(a,b))throw new AssertionError(a+" != "+b);}
    static void ok(boolean x){checks++;if(!x)throw new AssertionError("failed");}
    public static void main(String[] args) {
        String[] a = new String[0]; int n=0;
        a=MobileComboArrayOps.insert(a,n++,0,"b");
        String[] same=a;
        a=MobileComboArrayOps.insert(a,n++,0,"a");
        ok(same==a); eq(a[0],"a"); eq(a[1],"b");
        eq(MobileComboArrayOps.selectionAfterInsert(1,0),2);
        eq(MobileComboArrayOps.selectionAfterRemove(2,0,0),1);
        eq(MobileComboArrayOps.selectionAfterRemove(1,0,1),-1);
        MobileComboArrayOps.removeRange(a,n,0,0);n--;
        eq(a[0],"b");ok(a[1]==null);
        eq(MobileComboArrayOps.indexOf(a,n,"b",0),0);
        eq(MobileComboArrayOps.indexOf(a,n,"absent",0),-1);
        Random random=new Random(492);
        var oracle=new ArrayList<String>();String[] m=new String[0]; int count=0;
        for(int iteration=0;iteration<100_000;iteration++) {
            if(oracle.size()<500 && (oracle.isEmpty()||random.nextBoolean())) {
                int index=random.nextInt(oracle.size()+1);String value="v"+random.nextInt(35);
                m=MobileComboArrayOps.insert(m,count,index,value);count++;oracle.add(index,value);
            } else {
                int first=random.nextInt(oracle.size());int last=first+random.nextInt(oracle.size()-first);
                MobileComboArrayOps.removeRange(m,count,first,last);
                count-=last-first+1;oracle.subList(first,last+1).clear();
            }
            eq(count,oracle.size());
            for(int i=0;i<count;i++)eq(m[i],oracle.get(i));
            for(int i=count;i<m.length;i++)ok(m[i]==null);
            if(count>0){String target=oracle.get(random.nextInt(count));eq(MobileComboArrayOps.indexOf(m,count,target,0),oracle.indexOf(target));}
        }
        System.out.println("PASS " + checks + " Combo array, selection, stale-reference and randomized mutation checks");
    }
}

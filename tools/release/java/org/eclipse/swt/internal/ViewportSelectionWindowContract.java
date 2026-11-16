/* SPDX-FileCopyrightText: 2026 Hitesh Soliwal and Contributors to the Synexia Project
 * SPDX-License-Identifier: Apache-2.0 OR EPL-2.0
 */
package org.eclipse.swt.internal;

import java.util.Arrays;
import java.util.Random;
import org.eclipse.swt.internal.ViewportRuntime.Selection;

/** Java21 native-independent test of the *actual* SWT ViewportRuntime.Selection owner. */
public final class ViewportSelectionWindowContract {
    private ViewportSelectionWindowContract() { }

    private static void verify(boolean ok, String where) {
        if (!ok) throw new AssertionError(where);
    }

    private static void sample(Selection model, boolean[] expected, int from, int end) {
        int count = 0;
        for (int row=from; row<end; row++) if (expected[row]) count++;
        verify(model.selectedCount(from,end)==count,"window selectedCount");
        int[] a = new int[count+2];
        Arrays.fill(a, -11);
        verify(model.copySelectedIndices(from,end,a,1)==count,"copy return");
        verify(a[0]==-11 && a[a.length-1]==-11,"outside destination");
        int i=1;
        for(int row=from;row<end;row++)if(expected[row])verify(a[i++]==row,"window order");
        verify(Arrays.equals(model.toArray(from,end),Arrays.copyOfRange(a,1,1+count)),"array overload");
    }

    private static void maximum() {
        Selection model=new Selection();
        model.setLogicalCount(Integer.MAX_VALUE);
        model.selectAll();
        model.deselectRange(0,Integer.MAX_VALUE-10);
        model.deselectRange(Integer.MAX_VALUE-3,Integer.MAX_VALUE);
        int start=Integer.MAX_VALUE-20, end=Integer.MAX_VALUE;
        int[] actual=model.toArray(start,end);
        verify(actual.length==7,"huge window size");
        for(int i=0;i<7;i++)verify(actual[i]==Integer.MAX_VALUE-10+i,"huge row identity");
        int[] target=new int[10];
        Arrays.fill(target,-3);
        verify(model.copySelectedIndices(start,end,target,2)==7,"huge writer");
        int[] frozen=target.clone();
        try {model.copySelectedIndices(start,end,target,4);throw new AssertionError("missing range rejection");}
        catch(IndexOutOfBoundsException expected){verify(Arrays.equals(frozen,target),"atomic failure");}
        verify(model.logicalCount()==Integer.MAX_VALUE,"no global materialization");
    }

    private static void randomized() {
        Random random=new Random(0x20261116L);
        long count=0;
        for(int round=0;round<600;round++){
            int n=random.nextInt(130);
            Selection model=new Selection();
            model.setLogicalCount(n);
            boolean[] expected=new boolean[n];
            for(int step=0;step<80;step++){
                int a=random.nextInt(n+1),b=random.nextInt(n+1);
                int from=Math.min(a,b),to=Math.max(a,b);
                switch(random.nextInt(6)){
                    case 0 -> {model.selectRange(from,to);Arrays.fill(expected,from,to,true);}
                    case 1 -> {model.deselectRange(from,to);Arrays.fill(expected,from,to,false);}
                    case 2 -> {model.selectAll();Arrays.fill(expected,true);}
                    case 3 -> {model.clear();Arrays.fill(expected,false);}
                    case 4 -> {model.invert();for(int i=0;i<n;i++)expected[i]=!expected[i];}
                    default -> {if(n>0){
                        int row=random.nextInt(n);
                        boolean selected=random.nextBoolean();
                        model.setSelected(row,selected);
                        expected[row]=selected;
                    }}
                }
                for(int query=0;query<4;query++){
                    int x=random.nextInt(n+1),y=random.nextInt(n+1);
                    sample(model,expected,Math.min(x,y),Math.max(x,y));
                    count++;
                }
            }
        }
        System.out.println("SWT_SELECTION_WINDOW_RECEIVING queries="+count+" result=PASS");
    }

    public static void main(String[] args) {
        maximum();
        randomized();
        System.out.println("SWT_SELECTION_WINDOW_CONTRACT result=PASS");
    }
}

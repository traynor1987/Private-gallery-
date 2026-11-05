package uk.co.traynor.novaos.preview;
import java.util.*;
/** Pure rules shared by drag preview and commit; never mutate the caller's layout. */
public final class PolishRules {
 public static int edgePage(int page,int count,float fraction){return Math.max(0,Math.min(count-1,page+(fraction<.08f?-1:fraction>.92f?1:0)));}
 public static int uniqueSource(String title,List<String> titles){if(title==null||title.trim().isEmpty())return -1;int found=-1;for(int i=0;i<titles.size();i++)if(title.equals(titles.get(i))){if(found!=-1)return -1;found=i;}return found;}
 public static List<int[]> relocate(List<int[]> source,int index,int[] target){
  if(index<0||index>=source.size()||!WidgetRules.fits(target,Collections.emptyList(),-1))return null;
  List<int[]> result=new ArrayList<>();for(int[] r:source)result.add(r.clone());result.set(index,target.clone());
  List<int[]> occupied=new ArrayList<>();occupied.add(target);List<Integer> displaced=new ArrayList<>();
  for(int i=0;i<source.size();i++)if(i!=index){int[] r=source.get(i);if(WidgetRules.overlap(r,target))displaced.add(i);else occupied.add(r);}
  for(int i:displaced){int[] old=source.get(i),next=WidgetRules.firstFree(old[2],old[3],occupied);if(next==null)return null;result.set(i,next);occupied.add(next);}
  return result;
 }
}

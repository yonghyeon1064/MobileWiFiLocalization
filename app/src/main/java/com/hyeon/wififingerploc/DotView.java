package com.hyeon.wififingerploc;

import android.util.Log;
import android.view.View;
import java.util.List;
import java.util.ArrayList;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Canvas;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import java.util.HashMap;


public class DotView extends View {
    public List<Dot> dots = new ArrayList<>();
    private final Paint dotPaint = new Paint();

    // 점 찍을 때 확용하는 변수
    private Dot currentDot;
    // 지금까지 찍은 dot들 list
    // 현재 wifi data
    public HashMap<String, Integer> currentApDataMap = new HashMap<>();
    // 가장 최근의 currentDot의 Index
    public int savedDot;
    // localization 때 position이 update 되었는지
    public int positionUpdated = 0;
    // 현재 가장 가까운 점의 index, localization 모드 때 print 될 dot
    public int currentPositionIndex;
    // getDifference() 연산에 활용
    private int tempNum = 0;
    private int tempNum2 = 0;

    public DotView(Context context) {
        super(context);
        init();
    }

    public DotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DotView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        dotPaint.setColor(Color.BLUE);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(((MainActivity)MainActivity.mContext).applicationState == MainActivity.ApplicationState.Wardriving){
            Log.d("DotView", "Wardriving draw");
            for (Dot dot : dots) {
                canvas.drawCircle(dot.getX(), dot.getY(), 15, dotPaint);
            }
        } else if (((MainActivity)MainActivity.mContext).applicationState == MainActivity.ApplicationState.Localizaion) {
            Log.d("DotView", "Localization draw");
            canvas.drawCircle(dots.get(currentPositionIndex).getX(), dots.get(currentPositionIndex).getY(), 15, dotPaint);
        } else if (((MainActivity)MainActivity.mContext).applicationState == MainActivity.ApplicationState.Initial) {
            dots.clear();
        }

    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if(((MainActivity)MainActivity.mContext).applicationState == MainActivity.ApplicationState.Pause){
            return true;
        }
        float x = event.getX();
        float y = event.getY();

        // wardriving 모드일 때 touch 시 처리
        if( ((MainActivity)MainActivity.mContext).applicationState == MainActivity.ApplicationState.Wardriving ){
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    currentDot = getDotAtPosition(x, y);
                    // dot 없는 곳 touch
                    if (currentDot == null) {
                        currentDot = new Dot(x, y);
                        dots.add(currentDot);
                        invalidate();
                        ((MainActivity)MainActivity.mContext).displayPopupWindow(2);
                    }
                    else{ // 기존 dot touch
                        Log.d("DotView", "touch dot");
                        ((MainActivity)MainActivity.mContext).displayPopupWindow(3);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    savedDot = dots.indexOf(currentDot);
                    currentDot = null;
                    return true;
            }
        }

        return super.onTouchEvent(event);
    }

    private Dot getDotAtPosition(float x, float y) {
        for (Dot dot : dots) {
            if (Math.hypot(dot.getX() - x, dot.getY() - y) <= 15) {
                return dot;
            }
        }
        return null;
    }

    public void removeDotFromList(int dot){
        dots.remove(dot);
    }

    public static class Dot {
        private final float x;
        private final float y;
        public String ap;

        public HashMap<String, Integer> apListMap = new HashMap<>();

        public Dot(float x, float y) {
            this.x = x;
            this.y = y;
        }

        public float getX() {
            return x;
        }

        public float getY() {
            return y;
        }

        public String retAp(){
            return ap;
        }
    }

    public void getApData(String apString, Dot dot){
        dot.ap = apString;
        dot.apListMap.clear();
        String[] tempArray = apString.split(";");

        for(int i=4; i<tempArray.length; i=i+4){
            String temp = tempArray[i].substring(1, 3);
            dot.apListMap.put(tempArray[i-3], Integer.parseInt(temp));
        }
        Log.d("GetApData", "getApData");
    }

    public void setCurrentAp(String apString){
        currentApDataMap.clear();

        String[] tempArray = apString.split(";");

        for(int i=4; i<tempArray.length; i=i+4){
            String temp = tempArray[i].substring(1, 3);
            currentApDataMap.put(tempArray[i-3], Integer.parseInt(temp));
        }
        Log.d("SetCurrentAp", Integer.toString(currentApDataMap.size()));
        positionUpdated = 1;
    }

    public void setClosestdot(){
        Log.d("SetClosestDot", "setClosestdot");
        if(positionUpdated == 0){
            Log.e("SetClosestDot", "error happen");
            return;
        }
        if(dots.size() <= 1){
            Log.e("SetClosestDot", "너무 적은 dots");
            return;
        }

        tempNum2 = getDifference(dots.get(0));
        currentPositionIndex = 0;
        for(int i=1; i<dots.size(); i++){
            if(getDifference(dots.get(i)) < tempNum2){
                currentPositionIndex = i;
            }
        }
        Log.d("SetClosestDot", Integer.toString(currentPositionIndex));
        invalidate();
        positionUpdated = 0;
    }

    private int getDifference(Dot dot1){
        tempNum = 0;
        tempNum2 = 0;

        for(String key : currentApDataMap.keySet()){
            if(dot1.apListMap.containsKey(key)){
                int temp = dot1.apListMap.get(key) - currentApDataMap.get(key);
                tempNum += (int)Math.round(Math.pow(temp, 2));
                tempNum2++;
            }
        }

        Log.d("GetDifference", Integer.toString(dots.indexOf(dot1)));
        Log.d("GetDifference", Integer.toString(dot1.apListMap.size()));
        Log.d("GetDifference", Integer.toString(tempNum/tempNum2));

        return tempNum/tempNum2;
    }


}
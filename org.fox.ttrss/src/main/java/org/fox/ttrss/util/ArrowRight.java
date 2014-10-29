package org.fox.ttrss.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.util.AttributeSet;
import android.view.View;

//http://stackoverflow.com/questions/24723040/how-to-create-a-right-facing-arrow-using-xml-shapes-in-android

public class ArrowRight extends View {

    private Paint arrowPaint;
    private Path arrowPath;
    private int arrowColor = 0xFF888888;
    private float density;
    private int diameter = 5, diameter_calc, radius_calc;

    public ArrowRight(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        stuff();
    }

    public ArrowRight(Context context, AttributeSet attrs) {
        super(context, attrs);
        stuff();
    }

    public ArrowRight(Context context) {
        super(context);
        stuff();
    }

    private void stuff() {

        //Getting density "dp"
        density = getContext().getResources().getDisplayMetrics().scaledDensity;
        //Calculating actual diameter
        diameter_calc = (int) density * diameter;
        radius_calc = diameter/2;

        //Creating paint
        arrowPaint = new Paint();
        arrowPaint.setAntiAlias(true);
        arrowPaint.setColor(arrowColor);

        //Initialize path
        arrowPath = new Path();

        this.setWillNotDraw(false);
    }

    private int startX,startY, currentX, currentY;

    protected void onDraw(Canvas c) {

        startX = c.getWidth();
        startY = c.getHeight()/2;

        c.rotate(-45, startX, startY);

        arrowPath.reset();
        currentX = startX;
        currentY = startY;
        //Move to right end side center of the canvas
        arrowPath.moveTo(currentX,currentY);
        //Lets move up
        currentY = radius_calc;
        arrowPath.lineTo(currentX, currentY);
        //Now draw circle
        currentX-=radius_calc;
        arrowPath.addCircle(currentX, radius_calc, radius_calc, Direction.CCW);
        currentX-=radius_calc;

        arrowPath.lineTo(currentX,currentY);
        // Go to inner side center point
        currentX = startX - diameter_calc;
        currentY = startY - diameter_calc;
        arrowPath.lineTo(currentX,currentY);
        // Go left
        currentX = startX - startY + radius_calc;
        arrowPath.lineTo(currentX, currentY);
        //Draw circle
        currentY+=radius_calc;
        c.drawCircle(currentX, currentY, radius_calc, arrowPaint);
        currentY+=radius_calc;
        arrowPath.lineTo(currentX, currentY);
        //Go to start
        arrowPath.lineTo(startX, startY);

        c.drawPath(arrowPath, arrowPaint);
    }

}
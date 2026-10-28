package com.lt5lyrics.client.island;

/** Pure perspective projection for the passive fixed-lyrics edge indicator. */
public final class WorldIndicator {
    public record Edge(double x, double y) {}
    private WorldIndicator() {}
    public static Edge edge(double right, double up, double forward, double verticalFovDegrees, double aspect) {
        if (!Double.isFinite(right) || !Double.isFinite(up) || !Double.isFinite(forward)) return null;
        double tanY = Math.tan(Math.toRadians(Math.clamp(verticalFovDegrees, 10.0, 170.0) * .5));
        double tanX = tanY * Math.max(.1, aspect);
        double x, y;
        if (forward > .001) {
            x=right/(forward*tanX); y=-up/(forward*tanY);
            if(Math.abs(x)<=.96 && Math.abs(y)<=.96)return null;
        } else {
            x=right/tanX; y=-up/tanY;
            if(Math.abs(x)+Math.abs(y)<.0001)x=1.0;
        }
        double largest=Math.max(Math.abs(x),Math.abs(y));
        return largest<=0?null:new Edge(x/largest,y/largest);
    }
    public static float distanceMultiplier(boolean enabled, boolean following,double reference,double current) {
        if(!enabled || following || !Double.isFinite(reference) || !Double.isFinite(current) || reference<=.01 || current<0)return 1.0F;
        return (float)Math.clamp(current/reference,.65,2.0);
    }
}

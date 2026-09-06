package com.example.functions;

import com.caritasem.ruleuler.function.RuleFunction;
import com.caritasem.ruleuler.function.RuleMethod;
import com.caritasem.ruleuler.function.RuleParam;
import org.springframework.stereotype.Component;

@Component("geoFunctions")
@RuleFunction(bean = "geoFunctions", label = "地理函数")
public class GeoFunctions {

    @RuleMethod(label = "两地距离")
    public Double distanceKm(
            @RuleParam("出发纬度") Double lat1,
            @RuleParam("出发经度") Double lng1,
            @RuleParam("到达纬度") Double lat2,
            @RuleParam("到达经度") Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}

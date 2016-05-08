package ar.edu.itba.it.proyectofinal.tix_time.util;

import java.time.LocalTime;
import java.util.function.Supplier;

public class TixTimeUitl {
	public static final Supplier<Long> NANOS_OF_DAY = () -> LocalTime.now().toNanoOfDay();
}

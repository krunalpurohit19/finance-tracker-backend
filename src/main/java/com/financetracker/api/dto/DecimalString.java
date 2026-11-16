package com.financetracker.api.dto;

import com.fasterxml.jackson.annotation.JacksonAnnotationsInside;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A BigDecimal sent as a JSON string ("450.0000"), never a JSON number: the mobile client parses
 * money with decimal.js and would lose precision (or crash) on a number. Plain notation is global
 * (spring.jackson.generator.write-bigdecimal-as-plain).
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@JacksonAnnotationsInside
@JsonFormat(shape = JsonFormat.Shape.STRING)
public @interface DecimalString {}

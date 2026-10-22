package com.open.spring.system.kit;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class whose startup runners must run even when its feature is disabled, typically
 * schema migrations: the tables of disabled features still exist (so foreign keys and backups
 * keep working) and must stay in step with the entities.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface KitAlwaysRun {
}

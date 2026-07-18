package devops.iam.authorization;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** 声明业务动作与参数位置，由统一拦截器完成授权请求组装。 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireAuthorization {
    String action();
    String resourceType();
    int tenantIdArgument();
    int resourceIdArgument() default -1;
}

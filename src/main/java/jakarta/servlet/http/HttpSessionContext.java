package jakarta.servlet.http;

import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 在jakarta.servlet.http 实际上是没有这个HttpSessionContext，因为不安全！</br>
 * 仅仅是为了com.github.netty.protocol.servlet.ServletHttpSession不报错！
 */
@Deprecated
public interface HttpSessionContext {
	
	/**
    *
    * @deprecated 	As of Java Servlet API 2.1 with
    *			no replacement. This method must 
    *			return null and will be removed in
    *			a future version of this API.
    * @param sessionId the id of the session to be returned
    *
    * @return null in all cases
    */
   @Deprecated
   default HttpSession getSession(String sessionId) {
	   return null;
   }
   
   
   
 
   /**
    *
    * @deprecated	As of Java Servlet API 2.1 with
    *			no replacement. This method must return 
    *			an empty <code>Enumeration</code> and will be removed
    *			in a future version of this API.
    *
    * @return null 
    *
    */
   @Deprecated
   default Enumeration<String> getIds(){
	   List<String> emptyString = Collections.emptyList();
	   return Collections.enumeration(emptyString);
   }

}

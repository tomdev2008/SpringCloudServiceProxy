package com.zxj.cloud_service_proxy_core.util.invoke;


import com.zxj.cloud_service_proxy_core.exception.BaseExceptionInterface;
import com.zxj.cloud_service_proxy_core.exception.ServiceException;
import com.zxj.cloud_service_proxy_core.exception.ServiceRuntimeException;
import com.zxj.cloud_service_proxy_core.util.invoke.dto.ServiceDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

/**
 * 远程服务代理调用工具
 * @author zhuxiujie
 * @since 2017/12/6
 */
public class RemoteServiceProxyFactory implements InvocationHandler {

    private static Logger logger= LoggerFactory.getLogger(RemoteServiceProxyFactory.class);

    private RestTempleteProvider restTempleteProvider;

    public static final Map<String, String> feignMethodMap = new HashMap<>();


    public static <T> T newInstance(RestTempleteProvider provider, String remoteServiceMethod, Class<T> tClass) {
        Class[] interfaces = new Class[]{tClass};
        feignMethodMap.put(tClass.getName(), remoteServiceMethod);
        return (T) Proxy.newProxyInstance(RemoteServiceProxyFactory.class.getClassLoader(),
                interfaces, new RemoteServiceProxyFactory().setRestTempleteProvider(provider));
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String methodName = method.getName();
        Class[] paramsTypes = method.getParameterTypes();
        String _service = (String) BeanUtils.getProperty(BeanUtils.getProperty(method, "clazz"), "name");
        ServiceDTO serviceDTO = new ServiceDTO();
        serviceDTO.setMethod(methodName);
        serviceDTO.setService(_service);
        serviceDTO.setParams(args);
        serviceDTO.setParamsTypes(paramsTypes);
        byte[] bytes = SerializeStringUtil.serialize(serviceDTO);

        if(restTempleteProvider==null)throw new ServiceException("restTempleteProvider can not be null!");
        if(restTempleteProvider.getRestTemplete()==null)throw new ServiceException("can not restTempleteProvider.getRestTemplete()="+_service);
        try {
            logger.info("bytesLength:"+bytes.length);
            Class<? extends byte[]> classType=bytes.getClass();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            HttpEntity<byte[]> httpEntity = new HttpEntity<>(bytes, headers);
            ResponseEntity<? extends byte[]> response = ((RestTemplate)restTempleteProvider.getRestTemplete()).exchange("http://"+restTempleteProvider.service()+"/"+ getRemoteServiceMethod(_service), HttpMethod.PUT, httpEntity, classType);
            byte[] bytesResult= response.getBody();
            if(bytesResult==null)return null;
            Object result = SerializeStringUtil.deserialize(bytesResult);
            if (result instanceof BaseExceptionInterface) {
                ServiceException s;
                if (result instanceof ServiceException) {
                    s = (ServiceException) result;
                    throw new ServiceRuntimeException(s.getErrCode(), s.getErrMsg());
                } else if (result instanceof ServiceRuntimeException) {
                    throw (ServiceRuntimeException) result;
                }
            }
            return result;
        } catch (Exception e) {
            logger.error("远程代理服务"+_service+"调用失败",e);
           throw e;
        }
    }

    private String getRemoteServiceMethod(String service) {
        String method = feignMethodMap.get(service);
        return method;
    }

    public RemoteServiceProxyFactory setRestTempleteProvider(RestTempleteProvider restTempleteProvider) {
        this.restTempleteProvider = restTempleteProvider;
        return this;
    }
}
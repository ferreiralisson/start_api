import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import java.io.IOException;
import java.lang.annotation.*;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

// ====================== ANOTAÇÕES ======================
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface MyController {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@interface MyService {}

// ====================== SERVICE ======================
@MyService
class MessageService {
    public String getMessage() {
        return "Olá do serviço gerenciado pelo nosso mini Spring!";
    }
}

// ====================== CONTROLLER ======================
@MyController
class MessageController {
    private final MessageService service;

    // Injeção via construtor
    public MessageController(MessageService service) {
        this.service = service;
    }

    public String hello() {
        return service.getMessage();
    }
}

// ====================== IOC CONTAINER ======================
class MyApplicationContext {
    private final Map<Class<?>, Object> beans = new HashMap<>();

    public MyApplicationContext(Class<?>... components) {
        try {
            for (Class<?> clazz : components) {
                if (clazz.isAnnotationPresent(MyService.class) || clazz.isAnnotationPresent(MyController.class)) {
                    createBean(clazz);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Erro ao inicializar o contexto", e);
        }
    }

    private Object createBean(Class<?> clazz) throws Exception {
        if (beans.containsKey(clazz)) {
            return beans.get(clazz);
        }

        // Usa o construtor para injetar dependências
        Constructor<?> constructor = clazz.getConstructors()[0];
        Class<?>[] paramTypes = constructor.getParameterTypes();

        Object[] dependencies = new Object[paramTypes.length];
        for (int i = 0; i < paramTypes.length; i++) {
            dependencies[i] = createBean(paramTypes[i]);
        }

        Object bean = constructor.newInstance(dependencies);
        beans.put(clazz, bean);
        return bean;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBean(Class<T> clazz) {
        return (T) beans.get(clazz);
    }
}

// ====================== SERVLET (DISPATCHER) ======================
class DispatcherServlet extends HttpServlet {
    private final MessageController controller;

    public DispatcherServlet(MessageController controller) {
        this.controller = controller;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setContentType("text/plain");
        String path = req.getPathInfo();

        // Roteamento bem simples
        if ("/hello".equals(path)) {
            resp.getWriter().println(controller.hello());
        } else {
            resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
            resp.getWriter().println("404 - Not Found");
        }
    }
}

// ====================== MAIN APP ======================
public class MiniSpringApp {
    public static void main(String[] args) throws Exception {
        // Inicializa o container
        MyApplicationContext context = new MyApplicationContext(
                MessageService.class,
                MessageController.class
        );

        // Pega o controller pronto
        MessageController controller = context.getBean(MessageController.class);

        // Cria o servidor Jetty
        Server server = new Server(8080);
        ServletContextHandler contextHandler = new ServletContextHandler(ServletContextHandler.SESSIONS);
        contextHandler.setContextPath("/");
        contextHandler.addServlet(new ServletHolder(new DispatcherServlet(controller)), "/*");

        server.setHandler(contextHandler);
        server.start();
        server.join();
    }
}

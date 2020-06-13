package upickle.example;

import javax.xml.bind.annotation.XmlTransient;

public interface ComplexJavaInterface {
    public String getName();

    /** Tests nested-interface references */
    public JavaInterface getJavaInterface();

    /** Ignore @XxmlTransient methods */
    @XmlTransient public String getShadowedInterfaceMethod();
}
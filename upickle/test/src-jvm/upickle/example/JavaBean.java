package upickle.example;

import java.beans.Transient;
import java.util.ArrayList;
import java.util.List;
import javax.xml.bind.annotation.XmlTransient;

public class JavaBean implements ComplexJavaInterface {
    // Java Primitives
    protected String name;
    protected int number;
    protected Boolean bool;
    protected JavaEnum javaEnum;

    // Java Collections
    protected List<JavaBean> children;
    protected List<String> list;
    protected List<String> listWithoutSetter;

    // Java Class
    protected JavaClassWithInterface javaClassWithInterface;

    // Ignores - Transient
    @Transient protected String ignoredProtectedTransientAnnotationField;
    @XmlTransient protected String ignoredProtectedXmlTransientAnnotationField;
    protected transient String ignoredProtectedTransientField;

    // Other - Special

    /** Marked as @XmlTransient in ComplexJavaInterface */
    protected String shadowedInterfaceMethod;

    //
    // POJOs - Primitives
    //

    public String getName() {
        return name;
    }

    public void setName(String value) {
        this.name = value;
    }

    public int getNumber() {
        return number;
    }

    public void setNumber(int value) {
        this.number = value;
    }

    public Boolean isBool() {
        return bool;
    }

    public void setBool(Boolean value) {
        this.bool = value;
    }

    public JavaEnum getJavaEnum() {
        return javaEnum;
    }

    public void setJavaEnum(JavaEnum value) {
        this.javaEnum = value;
    }

    //
    // POJOs - Collections
    //

    public List<JavaBean> getChildren() {
        return children;
    }

    public void setChildren(List<JavaBean> value) {
        this.children = value;
    }

    public List<String> getList() {
        return list;
    }

    public void setList(List<String> value) {
        this.list = value;
    }

    public List<String> getListWithoutSetter() {
        if (listWithoutSetter == null) {
            listWithoutSetter = new ArrayList<String>();
        }
        return this.listWithoutSetter;
    }

    //
    // POJOs - Java Class
    //

    public void setJavaClassWithInterface(JavaClassWithInterface v) {
        javaClassWithInterface = v;
    }

    public JavaClassWithInterface getJavaClassWithInterface() {
        return javaClassWithInterface;
    }

    //
    // POJOs - Ignore Fields
    //

    @Transient protected String getIgnoredProtectedTransientAnnotationField() { return ignoredProtectedTransientAnnotationField; }
    @Transient protected String setIgnoredProtectedTransientAnnotationField(String value) { this.ignoredProtectedTransientAnnotationField = value; }

    @XmlTransient protected String getIgnoredProtectedXmlTransientAnnotationField() { return ignoredProtectedXmlTransientAnnotationField; }
    @XmlTransient protected String setIgnoredProtectedXmlTransientAnnotationField(String value) { this.ignoredProtectedXmlTransientAnnotationField = value; }

    protected transient String getIgnoredProtectedTransientField() { return ignoredProtectedTransientField; }
    protected transient String setIgnoredProtectedTransientField(String value) { this.ignoredProtectedTransientField = value; }


    @Transient public String getIgnoredPublicTransientAnnotationField() { return ignoredPublicTransientAnnotationField; }
    @Transient public String setIgnoredPublicTransientAnnotationField(String value) { this.ignoredPublicTransientAnnotationField = value; }

    @XmlTransient public String getIgnoredPublicXmlTransientAnnotationField() { return ignoredPublicXmlTransientAnnotationField; }
    @XmlTransient public String setIgnoredPublicXmlTransientAnnotationField(String value) { this.ignoredPublicXmlTransientAnnotationField = value; }

    public transient String getIgnoredPublicTransientField() { return ignoredPublicTransientField; }
    public transient String setIgnoredPublicTransientField(String value) { this.ignoredPublicTransientField = value; }

    //
    // Other - Special
    //

    /** Marked as @XmlTransient in the interface, should still be accessible */
    public String getShadowedInterfaceMethod() {
        return shadowedInterfaceMethod;
    }

    /** Marked as @XmlTransient in the interface, should still be setable */
    public void setShadowedInterfaceMethod(String value) {
        this.shadowedInterfaceMethod = value;
    }
}

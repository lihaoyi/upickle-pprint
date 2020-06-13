package upickle.example;

public class JavaClassWithInterface implements JavaInterface {
    protected String name;

    public void setName(String s) {
        name = s;
    }

    public String getName() {
        return name;
    }
}
# Dagger Integration

There are 3 ways to integrate Dagger components with Motif Scopes:

* Dagger Component as parent of Motif Scope
* Dagger Component as child of Motif Scope
* Dagger Component inside Motif Scope

## Dagger Component as parent of Motif Scope

For every Scope, Motif generates a `*ScopeImpl.Dependencies` interface, which declares the Scope's dependencies. The dependencies interface uses the same syntax as Dagger's Component [provision methods](https://google.github.io/dagger/api/2.14/dagger/Component.html#provision-methods) so a Dagger Component simply needs to implement the `*ScopeImpl.Dependencies` interface:

```java
@dagger.Component
interface Component extends MyScopeImpl.Dependencies {}

Component component = ...;
MyScope myScope = new MyScopeImpl(component);
```

## Dagger Component as child of Motif Scope

Since Motif access methods share the same syntax as Dagger Component provision methods, a Motif Scope can be declared as a Component dependency:

```java
@dagger.Component(dependencies = MyScope.class)
interface Component {}

MyScope myScope = ...;
Component component = DaggerComponent.builder()
        .myScope(myScope);
        .build();
```

## Dagger inside Motif Scope

If there is a bidirectional dependency between the Dagger Component and the Motif Scope (ie: Dagger needs an object provided by Motif and Motif needs an object provided by Dagger), you can make use of Motif's `@Spread` feature:

```java
@dagger.Component(dependencies = MyScope.class)
interface Component {

    DaggerDependency daggerDependency();
}

@motif.Scope
interface MyScope {

    abstract class Objects {
    
        abstract MotifDependency motifDependency();
        
        Foo foo(DaggerDependency daggerDependency) {
            return new Foo(daggerDependency);
        }

        @motif.Spread
        Component component(MyScope myScope) {
            return DaggerComponent.builder()
                    .myScope(myScope)
                    .build();
        }
    }
}
```

Above, `@Spread` tells Motif to provide all of the dependencies declared by the Component's provision methods in addition to the Component itself. With this pattern, it's possible to pass the Motif Scope into the Dagger Component as a dependency, while allowing Motif factory methods to rely on Dagger-provided objects.

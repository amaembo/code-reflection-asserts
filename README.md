This is an experimental assertions library based on [Project Babylon](https://openjdk.org/projects/babylon/).
The idea is to show a comprehensive message with intermediate subexpression results, using code reflection.

For example, you may want to write the following test:

```java
List<Integer> big = List.of(1, 2, 3, 4);
List<Integer> small = List.of(2, 5);
RefAsserts.assertTrue(() -> big.containsAll(small));
```

The resulting failure message will be the following:

```
java.lang.AssertionError: failed
big -> [1, 2, 3, 4]
small -> [2, 5]
big.containsAll(small) -> false
```

So you can easily see the result of sub-expression computation.

Build
===

To build the project, you need to build the custom JDK from Babylon [Git repository](https://github.com/openjdk/babylon) (use code-reflection branch).
Alternatively, you can use ready [binary builds](https://builds.shipilev.net/openjdk-jdk-babylon-code-reflection/) provided by incredible Mr. Shipilёv.

After that, you can build and test like a normal Maven project.

Note that given the experimental nature of Babylon project, incompatible changes could be introduced in Babylon, 
which may prevent this project from building. No guarantees about compatibility.

Support & Contribution
===

This project is mainly a proof of concept to explore the possibilities of Babylon project. 
It's not intended to be a production quality, so it's not recommended to depend on it.
If you experience problems with this project, you are welcome to report an issue or file a pull-request, 
but no support is guaranteed. If you like the project and want to make it a product, feel free to fork.
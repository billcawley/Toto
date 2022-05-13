<c:if test="${reports != null && sessionScope.test != null}">
    <script type="text/javascript" src="/quickview/bulma-quickview.min.js"></script>
    <script>
        var quickviews = bulmaQuickview.attach(); // quickviews now contains an array of all Quickview instances
    </script>
</c:if>

</body>
</html>
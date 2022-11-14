(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [847],
    {
        8553: function (n, t, e) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/databases",
                function () {
                    return e(4967);
                },
            ]);
        },
        67: function (n, t, e) {
            "use strict";
            e.d(t, {
                O: function () {
                    return i;
                },
            });
            var r = e(5893),
                s =
                    (e(7294),
                    function (n) {
                        return (0, r.jsx)("div", { className: "az-section-controls", children: n.children });
                    }),
                i = function (n) {
                    return (0, r.jsx)(r.Fragment, {
                        children: (0, r.jsxs)("div", {
                            className: "az-section-heading",
                            children: [
                                (0, r.jsx)("h3", { children: n.label }),
                                !!n.children && (0, r.jsx)(s, { children: n.children }),
                            ],
                        }),
                    });
                };
        },
        3777: function (n, t, e) {
            "use strict";
            e.d(t, {
                Z: function () {
                    return i;
                },
            });
            var r = e(4134),
                s = e(7294);
            function i(n) {
                var t = (0, s.useContext)(r.ZP).setLayout;
                (0, s.useEffect)(function () {
                    return (
                        t(n),
                        function () {
                            t({});
                        }
                    );
                }, []);
            }
        },
        4967: function (n, t, e) {
            "use strict";
            e.r(t),
                e.d(t, {
                    default: function () {
                        return u;
                    },
                });
            var r = e(5893),
                s = (e(7294), e(67)),
                i = function (n) {
                    return (0, r.jsx)("div", {
                        className: "az-databases-view",
                        children: (0, r.jsx)(s.O, { label: "Databases" }),
                    });
                },
                c = e(3777),
                u = function () {
                    return (0, c.Z)({ title: "Databases" }), (0, r.jsx)(i, {});
                };
        },
    },
    function (n) {
        n.O(0, [774, 888, 179], function () {
            return (t = 8553), n((n.s = t));
            var t;
        });
        var t = n.O();
        _N_E = t;
    },
]);

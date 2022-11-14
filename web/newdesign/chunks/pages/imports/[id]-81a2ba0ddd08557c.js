(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [442],
    {
        3387: function (n, t, r) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/imports/[id]",
                function () {
                    return r(7545);
                },
            ]);
        },
        67: function (n, t, r) {
            "use strict";
            r.d(t, {
                O: function () {
                    return u;
                },
            });
            var e = r(5893),
                i =
                    (r(7294),
                    function (n) {
                        return (0, e.jsx)("div", { className: "az-section-controls", children: n.children });
                    }),
                u = function (n) {
                    return (0, e.jsx)(e.Fragment, {
                        children: (0, e.jsxs)("div", {
                            className: "az-section-heading",
                            children: [
                                (0, e.jsx)("h3", { children: n.label }),
                                !!n.children && (0, e.jsx)(i, { children: n.children }),
                            ],
                        }),
                    });
                };
        },
        3777: function (n, t, r) {
            "use strict";
            r.d(t, {
                Z: function () {
                    return u;
                },
            });
            var e = r(4134),
                i = r(7294);
            function u(n) {
                var t = (0, i.useContext)(e.ZP).setLayout;
                (0, i.useEffect)(function () {
                    return (
                        t(n),
                        function () {
                            t({});
                        }
                    );
                }, []);
            }
        },
        7545: function (n, t, r) {
            "use strict";
            r.r(t),
                r.d(t, {
                    __N_SSG: function () {
                        return s;
                    },
                    default: function () {
                        return o;
                    },
                });
            var e = r(5893),
                i = (r(7294), r(67)),
                u = function (n) {
                    return (0, e.jsx)("div", {
                        className: "az-import-view",
                        children: (0, e.jsx)(i.O, { label: "Import" }),
                    });
                },
                c = r(3777),
                s = !0,
                o = function () {
                    return (0, c.Z)({ title: "Import" }), (0, e.jsx)(u, {});
                };
        },
    },
    function (n) {
        n.O(0, [774, 888, 179], function () {
            return (t = 3387), n((n.s = t));
            var t;
        });
        var t = n.O();
        _N_E = t;
    },
]);

(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [892],
    {
        6249: function (n, e, r) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/users",
                function () {
                    return r(5378);
                },
            ]);
        },
        67: function (n, e, r) {
            "use strict";
            r.d(e, {
                O: function () {
                    return u;
                },
            });
            var t = r(5893),
                s =
                    (r(7294),
                    function (n) {
                        return (0, t.jsx)("div", { className: "az-section-controls", children: n.children });
                    }),
                u = function (n) {
                    return (0, t.jsx)(t.Fragment, {
                        children: (0, t.jsxs)("div", {
                            className: "az-section-heading",
                            children: [
                                (0, t.jsx)("h3", { children: n.label }),
                                !!n.children && (0, t.jsx)(s, { children: n.children }),
                            ],
                        }),
                    });
                };
        },
        3777: function (n, e, r) {
            "use strict";
            r.d(e, {
                Z: function () {
                    return u;
                },
            });
            var t = r(4134),
                s = r(7294);
            function u(n) {
                var e = (0, s.useContext)(t.ZP).setLayout;
                (0, s.useEffect)(function () {
                    return (
                        e(n),
                        function () {
                            e({});
                        }
                    );
                }, []);
            }
        },
        5378: function (n, e, r) {
            "use strict";
            r.r(e),
                r.d(e, {
                    default: function () {
                        return c;
                    },
                });
            var t = r(5893),
                s = (r(7294), r(67)),
                u = function (n) {
                    return (0, t.jsx)("div", {
                        className: "az-users-view",
                        children: (0, t.jsx)(s.O, { label: "Users" }),
                    });
                },
                i = r(3777),
                c = function () {
                    return (0, i.Z)({ title: "Users" }), (0, t.jsx)(u, {});
                };
        },
    },
    function (n) {
        n.O(0, [774, 888, 179], function () {
            return (e = 6249), n((n.s = e));
            var e;
        });
        var e = n.O();
        _N_E = e;
    },
]);

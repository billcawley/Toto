(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [405],
    {
        8312: function (e, n, r) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/",
                function () {
                    return r(120);
                },
            ]);
        },
        1472: function (e, n, r) {
            "use strict";
            r.d(n, {
                n: function () {
                    return a;
                },
            });
            var s = r(1799),
                t = r(5893),
                i = (r(7294), r(1702)),
                a = function (e) {
                    return (0, t.jsx)("div", {
                        className: "az-report-cards",
                        children: e.reports.map(function (e) {
                            return (0, t.jsx)(i.x, (0, s.Z)({}, e), e.id);
                        }),
                    });
                };
        },
        6315: function (e, n, r) {
            "use strict";
            r.d(n, {
                E: function () {
                    return w;
                },
            });
            var s = r(5893),
                t = r(9687),
                i = r(6365),
                a = r(197),
                l = r(8945),
                o = r(7294),
                c = r(2837),
                d = r(1664),
                u = r.n(d),
                h = r(1472),
                x = r(3274),
                j = r(3059),
                f = r(67),
                p = r(3341),
                m = r(8438),
                v = r(9790),
                b = r(8917),
                w = function (e) {
                    var n = (0, o.useState)(""),
                        r = n[0],
                        d = n[1],
                        w = (0, o.useState)("list"),
                        _ = w[0],
                        N = w[1],
                        g = e.reports.filter(function (e) {
                            var n;
                            return null === (n = e.name) || void 0 === n
                                ? void 0
                                : n.toLowerCase().includes(null === r || void 0 === r ? void 0 : r.toLowerCase());
                        });
                    return (0, s.jsxs)(s.Fragment, {
                        children: [
                            (0, s.jsxs)(f.O, {
                                label: "Reports",
                                children: [(0, s.jsx)(j.Q, { onChange: d }), (0, s.jsx)(p.K, { onChange: N, view: _ })],
                            }),
                            (0, s.jsxs)(x.t, {
                                children: [
                                    "list" === _ &&
                                        (0, s.jsx)(m.i, {
                                            columns: ["Name", "Database", "Author", ""],
                                            pageSize: e.pageSize,
                                            label: "reports",
                                            children: (0, v.Y)(g, "name").map(function (e) {
                                                return (0, s.jsxs)(
                                                    "tr",
                                                    {
                                                        children: [
                                                            (0, s.jsx)("td", {
                                                                className: "full",
                                                                children: (0, s.jsx)("div", {
                                                                    children: (0, s.jsx)(u(), {
                                                                        href: "/reports/" + e.id,
                                                                        children: (0, s.jsxs)("a", {
                                                                            children: [
                                                                                (0, s.jsx)(t.Z, {}),
                                                                                (0, s.jsx)("p", { children: e.name }),
                                                                            ],
                                                                        }),
                                                                    }),
                                                                }),
                                                            }),
                                                            (0, s.jsx)("td", {
                                                                children: (0, s.jsx)("span", {
                                                                    className: "az-badge",
                                                                    children: e.database,
                                                                }),
                                                            }),
                                                            (0, s.jsx)("td", { children: e.author }),
                                                            (0, s.jsx)("td", {
                                                                children: (0, s.jsx)(c.e, {
                                                                    items: [
                                                                        {
                                                                            label: "Open",
                                                                            href: "/reports/".concat(e.id),
                                                                            icon: i.Z,
                                                                        },
                                                                        { label: "Download", href: b.tf, icon: a.Z },
                                                                        { label: "sep1", seperator: !0 },
                                                                        { label: "Delete", href: "#", icon: l.Z },
                                                                    ],
                                                                }),
                                                            }),
                                                        ],
                                                    },
                                                    e.id,
                                                );
                                            }),
                                        }),
                                    "grid" === _ && (0, s.jsx)(h.n, { reports: (0, v.Y)(g, "name") }),
                                ],
                            }),
                        ],
                    });
                };
        },
        120: function (e, n, r) {
            "use strict";
            r.r(n),
                r.d(n, {
                    default: function () {
                        return h;
                    },
                });
            var s = r(5893),
                t = r(5812),
                i = r(378),
                a = (r(7294), r(1472)),
                l = r(6315),
                o = r(3274),
                c = r(67),
                d = function (e) {
                    return (0, s.jsxs)("div", {
                        className: "az-dashboard-view",
                        children: [
                            (0, s.jsx)(c.O, { label: "Recently Viewed" }),
                            (0, s.jsx)(o.t, { children: (0, s.jsx)(a.n, { reports: t.Gm.slice(0, 3) }) }),
                            (0, s.jsx)(l.E, { reports: t.Gm, pageSize: 5 }),
                            (0, s.jsx)(i.o, { imports: t.o0, pageSize: 5 }),
                        ],
                    });
                },
                u = r(3777),
                h = function () {
                    return (0, u.Z)({ title: "Dashboard" }), (0, s.jsx)(d, {});
                };
        },
    },
    function (e) {
        e.O(0, [385, 774, 888, 179], function () {
            return (n = 8312), e((e.s = n));
            var n;
        });
        var n = e.O();
        _N_E = n;
    },
]);

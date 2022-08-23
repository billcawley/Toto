(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [53],
    {
        9702: function (e, n, r) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/reports",
                function () {
                    return r(9978);
                },
            ]);
        },
        3274: function (e, n, r) {
            "use strict";
            r.d(n, {
                t: function () {
                    return i;
                },
            });
            var t = r(5893),
                i =
                    (r(7294),
                    function (e) {
                        return (0, t.jsx)("div", { className: "az-section-body", children: e.children });
                    });
        },
        3059: function (e, n, r) {
            "use strict";
            r.d(n, {
                Q: function () {
                    return s;
                },
            });
            var t = r(5893),
                i = (r(7294), r(9743)),
                s = function (e) {
                    return (0, t.jsxs)("div", {
                        className: "az-section-filter",
                        children: [
                            (0, t.jsx)("div", { children: (0, t.jsx)(i.Z, {}) }),
                            (0, t.jsx)("input", {
                                type: "text",
                                placeholder: "Filter",
                                onChange: function (n) {
                                    return e.onChange && e.onChange(n.target.value);
                                },
                            }),
                        ],
                    });
                };
        },
        67: function (e, n, r) {
            "use strict";
            r.d(n, {
                O: function () {
                    return s;
                },
            });
            var t = r(5893),
                i =
                    (r(7294),
                    function (e) {
                        return (0, t.jsx)("div", { className: "az-section-controls", children: e.children });
                    }),
                s = function (e) {
                    return (0, t.jsx)(t.Fragment, {
                        children: (0, t.jsxs)("div", {
                            className: "az-section-heading",
                            children: [
                                (0, t.jsx)("h3", { children: e.label }),
                                !!e.children && (0, t.jsx)(i, { children: e.children }),
                            ],
                        }),
                    });
                };
        },
        3341: function (e, n, r) {
            "use strict";
            r.d(n, {
                K: function () {
                    return l;
                },
            });
            var t = r(5893),
                i = r(7294);
            var s = i.forwardRef(function (e, n) {
                return i.createElement(
                    "svg",
                    Object.assign(
                        {
                            xmlns: "http://www.w3.org/2000/svg",
                            fill: "none",
                            viewBox: "0 0 24 24",
                            strokeWidth: 2,
                            stroke: "currentColor",
                            "aria-hidden": "true",
                            ref: n,
                        },
                        e,
                    ),
                    i.createElement("path", {
                        strokeLinecap: "round",
                        strokeLinejoin: "round",
                        d: "M4 6h16M4 10h16M4 14h16M4 18h16",
                    }),
                );
            });
            var c = i.forwardRef(function (e, n) {
                    return i.createElement(
                        "svg",
                        Object.assign(
                            {
                                xmlns: "http://www.w3.org/2000/svg",
                                fill: "none",
                                viewBox: "0 0 24 24",
                                strokeWidth: 2,
                                stroke: "currentColor",
                                "aria-hidden": "true",
                                ref: n,
                            },
                            e,
                        ),
                        i.createElement("path", {
                            strokeLinecap: "round",
                            strokeLinejoin: "round",
                            d: "M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z",
                        }),
                    );
                }),
                a = r(4184),
                o = r.n(a),
                l = function (e) {
                    return (0, t.jsx)("div", {
                        className: "az-section-view",
                        children: (0, t.jsxs)("span", {
                            children: [
                                (0, t.jsx)("button", {
                                    onClick: function () {
                                        return e.onChange("list");
                                    },
                                    className: o()({ selected: "list" === e.view }),
                                    children: (0, t.jsx)(s, {}),
                                }),
                                (0, t.jsx)("button", {
                                    onClick: function () {
                                        return e.onChange("grid");
                                    },
                                    className: o()({ selected: "grid" === e.view }),
                                    children: (0, t.jsx)(c, {}),
                                }),
                            ],
                        }),
                    });
                };
        },
        8438: function (e, n, r) {
            "use strict";
            r.d(n, {
                i: function () {
                    return s;
                },
            });
            var t = r(5893),
                i = r(7294),
                s = function (e) {
                    var n,
                        r,
                        s = (0, i.useState)(!0),
                        c = (s[0], s[1], (0, i.useState)(0)),
                        a = c[0],
                        o = c[1],
                        l = e.children.length,
                        u = e.pageSize * a,
                        d = Math.min(e.pageSize * a + e.pageSize, l);
                    return (
                        (0, i.useEffect)(
                            function () {
                                o(0);
                            },
                            [l],
                        ),
                        (0, t.jsxs)("div", {
                            className: "az-table",
                            children: [
                                (0, t.jsxs)("table", {
                                    children: [
                                        (0, t.jsx)("thead", {
                                            children: (0, t.jsx)("tr", {
                                                children:
                                                    null === (n = e.columns) || void 0 === n
                                                        ? void 0
                                                        : n.map(function (e) {
                                                              return (0, t.jsx)("th", { children: e }, e);
                                                          }),
                                            }),
                                        }),
                                        (0, t.jsx)("tbody", { children: e.children.slice(u, d) }),
                                    ],
                                }),
                                !!l &&
                                    !(u < 2 && d === l) &&
                                    (0, t.jsxs)("nav", {
                                        children: [
                                            (0, t.jsx)("div", {
                                                children: (0, t.jsxs)("p", {
                                                    children: [
                                                        "Showing ",
                                                        (0, t.jsx)("strong", { children: u + 1 }),
                                                        " to ",
                                                        (0, t.jsx)("strong", { children: d }),
                                                        " of ",
                                                        (0, t.jsx)("strong", { children: l }),
                                                        " ",
                                                        null !== (r = e.label) && void 0 !== r ? r : "results",
                                                    ],
                                                }),
                                            }),
                                            (0, t.jsx)("div", {
                                                children: (0, t.jsxs)(t.Fragment, {
                                                    children: [
                                                        (0, t.jsx)("button", {
                                                            onClick: function () {
                                                                return o(a - 1);
                                                            },
                                                            disabled: u < 2,
                                                            children: "Previous",
                                                        }),
                                                        (0, t.jsx)("button", {
                                                            onClick: function () {
                                                                return o(a + 1);
                                                            },
                                                            disabled: d === l,
                                                            children: "Next",
                                                        }),
                                                    ],
                                                }),
                                            }),
                                        ],
                                    }),
                            ],
                        })
                    );
                };
        },
        1472: function (e, n, r) {
            "use strict";
            r.d(n, {
                n: function () {
                    return c;
                },
            });
            var t = r(1799),
                i = r(5893),
                s = (r(7294), r(1702)),
                c = function (e) {
                    return (0, i.jsx)("div", {
                        className: "az-report-cards",
                        children: e.reports.map(function (e) {
                            return (0, i.jsx)(s.x, (0, t.Z)({}, e), e.id);
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
            var t = r(5893),
                i = r(9687),
                s = r(6365),
                c = r(197),
                a = r(8945),
                o = r(7294),
                l = r(2837),
                u = r(1664),
                d = r.n(u),
                h = r(1472),
                f = r(3274),
                x = r(3059),
                j = r(67),
                v = r(3341),
                p = r(8438),
                g = r(9790),
                m = r(8917),
                w = function (e) {
                    var n = (0, o.useState)(""),
                        r = n[0],
                        u = n[1],
                        w = (0, o.useState)("list"),
                        b = w[0],
                        z = w[1],
                        N = e.reports.filter(function (e) {
                            var n;
                            return null === (n = e.name) || void 0 === n
                                ? void 0
                                : n.toLowerCase().includes(null === r || void 0 === r ? void 0 : r.toLowerCase());
                        });
                    return (0, t.jsxs)(t.Fragment, {
                        children: [
                            (0, t.jsxs)(j.O, {
                                label: "Reports",
                                children: [(0, t.jsx)(x.Q, { onChange: u }), (0, t.jsx)(v.K, { onChange: z, view: b })],
                            }),
                            (0, t.jsxs)(f.t, {
                                children: [
                                    "list" === b &&
                                        (0, t.jsx)(p.i, {
                                            columns: ["Name", "Database", "Author", ""],
                                            pageSize: e.pageSize,
                                            label: "reports",
                                            children: (0, g.Y)(N, "name").map(function (e) {
                                                return (0, t.jsxs)(
                                                    "tr",
                                                    {
                                                        children: [
                                                            (0, t.jsx)("td", {
                                                                className: "full",
                                                                children: (0, t.jsx)("div", {
                                                                    children: (0, t.jsx)(d(), {
                                                                        href: "/api/Online?reportid=" + e.id + "&newdesign=true&database=".concat(e.database),
                                                                        children: (0, t.jsxs)("a", {
                                                                            children: [
                                                                                (0, t.jsx)(i.Z, {}),
                                                                                (0, t.jsx)("p", { children: e.name }),
                                                                            ],
                                                                        }),
                                                                    }),
                                                                }),
                                                            }),
                                                            (0, t.jsx)("td", {
                                                                children: (0, t.jsx)("span", {
                                                                    className: "az-badge",
                                                                    children: e.database,
                                                                }),
                                                            }),
                                                            (0, t.jsx)("td", { children: e.author }),
                                                            (0, t.jsx)("td", {
                                                                children: (0, t.jsx)(l.e, {
                                                                    items: [
                                                                        {
                                                                            label: "Open",
                                                                            href: "/api/Online?reportid=" + e.id + "&newdesign=true&database=".concat(e.database),
                                                                            icon: s.Z,
                                                                        },
                                                                        { label: "Download", href: "/api/DownloadTemplate?reportId=" + e.id, icon: c.Z },
                                                                        { label: "sep1", seperator: !0 },
                                                                        { label: "Delete", href: "/api/ManageReports?deleteId=" + e.id, icon: a.Z },
                                                                    ],
                                                                }),
                                                            }),
                                                        ],
                                                    },
                                                    e.id,
                                                );
                                            }),
                                        }),
                                    "grid" === b && (0, t.jsx)(h.n, { reports: (0, g.Y)(N, "name") }),
                                ],
                            }),
                        ],
                    });
                };
        },
        3777: function (e, n, r) {
            "use strict";
            r.d(n, {
                Z: function () {
                    return s;
                },
            });
            var t = r(4134),
                i = r(7294);
            function s(e) {
                var n = (0, i.useContext)(t.ZP).setLayout;
                (0, i.useEffect)(function () {
                    return (
                        n(e),
                        function () {
                            n({});
                        }
                    );
                }, []);
            }
        },
        9978: function (e, n, r) {
            "use strict";
            r.r(n),
                r.d(n, {
                    default: function () {
                        return o;
                    },
                });
            var t = r(5893),
                i = (r(7294), r(6315)),
                s = r(5812),
                c = function (e) {
                    return (0, t.jsx)("div", {
                        className: "az-reports-view",
                        children: (0, t.jsx)(i.E, { reports: s.Gm, pageSize: 10 }),
                    });
                },
                a = r(3777),
                o = function () {
                    return (0, a.Z)({ title: "Reports" }), (0, t.jsx)(c, {});
                };
        },
    },
    function (e) {
        e.O(0, [774, 888, 179], function () {
            return (n = 9702), e((e.s = n));
            var n;
        });
        var n = e.O();
        _N_E = n;
    },
]);

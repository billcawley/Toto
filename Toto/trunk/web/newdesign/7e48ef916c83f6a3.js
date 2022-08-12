(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [193],
    {
        7321: function (e, n, r) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/reports/[id]",
                function () {
                    return r(9917);
                },
            ]);
        },
        7065: function (e, n, r) {
            "use strict";
            r.d(n, {
                g: function () {
                    return c;
                },
            });
            var t = r(5893),
                i = r(7294);
            var o = i.forwardRef(function (e, n) {
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
                            d: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
                        }),
                    );
                }),
                a = r(8917),
                s = function () {
                    return (0, t.jsx)("img", { className: "az-spinner", src: a.lY });
                },
                c = function (e) {
                    var n,
                        r = (0, i.useState)(!1),
                        a = r[0],
                        c = r[1];
                    return e.isLoading
                        ? (0, t.jsx)(t.Fragment, {
                              children: (0, t.jsxs)("div", {
                                  className: "az-loading",
                                  children: [
                                      (0, t.jsxs)("div", {
                                          className: "az-loading-info",
                                          children: [
                                              (0, t.jsx)(s, {}),
                                              !!e.percentage &&
                                                  (0, t.jsxs)("span", {
                                                      children: [e.percentage <= 100 ? e.percentage : 100, "%"],
                                                  }),
                                          ],
                                      }),
                                      !!(null === (n = e.log) || void 0 === n ? void 0 : n.length) &&
                                          (0, t.jsxs)("div", {
                                              className: "az-logging-info",
                                              children: [
                                                  (0, t.jsx)("div", {
                                                      children: (0, t.jsxs)("button", {
                                                          onClick: function () {
                                                              e.onShowLog && e.onShowLog(!a), c(!a);
                                                          },
                                                          children: [
                                                              (0, t.jsx)(o, {}),
                                                              " ",
                                                              a ? "Hide" : "Show",
                                                              " detail",
                                                          ],
                                                      }),
                                                  }),
                                                  a &&
                                                      (0, t.jsx)("div", {
                                                          children: (0, t.jsx)("ul", {
                                                              children: e.log.map(function (e) {
                                                                  return (0, t.jsx)("li", { children: e }, e);
                                                              }),
                                                          }),
                                                      }),
                                              ],
                                          }),
                                  ],
                              }),
                          })
                        : (0, t.jsx)(t.Fragment, { children: e.children });
                };
        },
        3777: function (e, n, r) {
            "use strict";
            r.d(n, {
                Z: function () {
                    return o;
                },
            });
            var t = r(4134),
                i = r(7294);
            function o(e) {
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
        9917: function (e, n, r) {
            "use strict";
            r.r(n),
                r.d(n, {
                    __N_SSG: function () {
                        return x;
                    },
                    default: function () {
                        return j;
                    },
                });
            var t = r(5893),
                i = r(1799),
                o = r(7294),
                a = r(197);
            var s = o.forwardRef(function (e, n) {
                return o.createElement(
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
                    o.createElement("path", {
                        strokeLinecap: "round",
                        strokeLinejoin: "round",
                        d: "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z",
                    }),
                );
            });
            var c = o.forwardRef(function (e, n) {
                    return o.createElement(
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
                        o.createElement("path", {
                            strokeLinecap: "round",
                            strokeLinejoin: "round",
                            d: "M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z",
                        }),
                    );
                }),
                u = r(7161),
                l = r(3737),
                d = r(6896),
                f = r(7065),
                h = r(8917),
                v = r(4951);
            var g = function (e) {
                    var n = (0, o.useState)(!0),
                        r = n[0],
                        i = n[1],
                        g = (0, o.useState)(0),
                        m = g[0],
                        p = g[1],
                        w = null;
                    return (
                        (function (e) {
                            var n = (0, o.useContext)(v.ZP).setTopBar;
                            (0, o.useEffect)(function () {
                                return (
                                    n(e),
                                    function () {
                                        n({});
                                    }
                                );
                            }, []);
                        })({
                            menu: [
                                { label: "Download", href: "#", icon: a.Z },
                                {
                                    label: "File",
                                    items: [
                                        { label: "Download", href: "#", icon: a.Z },
                                        { label: "sep1", seperator: !0 },
                                        { label: "Lock", href: "#", icon: s },
                                        { label: "Unlock", href: "#", icon: c },
                                    ],
                                },
                                {
                                    label: "Database",
                                    items: [
                                        { label: "Audit", href: "#", icon: u.Z },
                                        { label: "Inspect", href: "#", icon: l.Z },
                                    ],
                                },
                                { label: "Template", items: [{ label: "View", href: "#", icon: d.Z }] },
                            ],
                        }),
                        (0, o.useEffect)(
                            function () {
                                i(!0), p(0);
                            },
                            [e.id],
                        ),
                        (0, o.useEffect)(
                            function () {
                                return (
                                    r &&
                                        (w = setInterval(function () {
                                            p(function (e) {
                                                return e + 1;
                                            });
                                        }, 25)),
                                    function () {
                                        w && clearInterval(w);
                                    }
                                );
                            },
                            [r],
                        ),
                        (0, o.useEffect)(
                            function () {
                                m >= 100 && (i(!1), p(0));
                            },
                            [m],
                        ),
                        (0, t.jsx)("div", {
                            className: "az-report",
                            children: (0, t.jsx)(f.g, {
                                isLoading: r,
                                percentage: m,
                                children: (0, t.jsx)("iframe", {
                                    src: h.gt,
                                    width: "100%",
                                    height: "100%",
                                    frameBorder: "0",
                                }),
                            }),
                        })
                    );
                },
                m = r(1163),
                p = function (e) {
                    var n = (0, m.useRouter)(),
                        r = {
                            author: "",
                            database: "",
                            description: "",
                            id: parseInt(null === n || void 0 === n ? void 0 : n.query.id),
                            name: "",
                        };
                    return (0, t.jsx)("div", { className: "az-report-view", children: (0, t.jsx)(g, (0, i.Z)({}, r)) });
                },
                w = r(3777),
                x = !0,
                j = function () {
                    return (0, w.Z)({ title: "Report", compact: !0 }), (0, t.jsx)(p, {});
                };
        },
    },
    function (e) {
        e.O(0, [774, 888, 179], function () {
            return (n = 7321), e((e.s = n));
            var n;
        });
        var n = e.O();
        _N_E = n;
    },
]);

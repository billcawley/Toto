(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [193],
    {
        7321: function (e, n, t) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/reports/[id]",
                function () {
                    return t(9122);
                },
            ]);
        },
        7065: function (e, n, t) {
            "use strict";
            t.d(n, {
                g: function () {
                    return c;
                },
            });
            var r = t(5893),
                o = t(7294);
            var i = o.forwardRef(function (e, n) {
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
                            d: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
                        }),
                    );
                }),
                s = t(8917),
                a = function () {
                    return (0, r.jsx)("img", { className: "az-spinner", src: s.lY });
                },
                c = function (e) {
                    var n,
                        t = (0, o.useState)(!1),
                        s = t[0],
                        c = t[1];
                    return e.isLoading
                        ? (0, r.jsx)(r.Fragment, {
                              children: (0, r.jsxs)("div", {
                                  className: "az-loading",
                                  children: [
                                      (0, r.jsxs)("div", {
                                          className: "az-loading-info",
                                          children: [
                                              (0, r.jsx)(a, {}),
                                              !!e.percentage &&
                                                  (0, r.jsxs)("span", {
                                                      children: [e.percentage <= 100 ? e.percentage : 100, "%"],
                                                  }),
                                          ],
                                      }),
                                      !!(null === (n = e.log) || void 0 === n ? void 0 : n.length) &&
                                          (0, r.jsxs)("div", {
                                              className: "az-logging-info",
                                              children: [
                                                  (0, r.jsx)("div", {
                                                      children: (0, r.jsxs)("button", {
                                                          onClick: function () {
                                                              e.onShowLog && e.onShowLog(!s), c(!s);
                                                          },
                                                          children: [
                                                              (0, r.jsx)(i, {}),
                                                              " ",
                                                              s ? "Hide" : "Show",
                                                              " detail",
                                                          ],
                                                      }),
                                                  }),
                                                  s &&
                                                      (0, r.jsx)("div", {
                                                          children: (0, r.jsx)("ul", {
                                                              children: e.log.map(function (e) {
                                                                  return (0, r.jsx)("li", { children: e }, e);
                                                              }),
                                                          }),
                                                      }),
                                              ],
                                          }),
                                  ],
                              }),
                          })
                        : (0, r.jsx)(r.Fragment, { children: e.children });
                };
        },
        3777: function (e, n, t) {
            "use strict";
            t.d(n, {
                Z: function () {
                    return i;
                },
            });
            var r = t(4134),
                o = t(7294);
            function i(e) {
                var n = (0, o.useContext)(r.ZP).setLayout;
                (0, o.useEffect)(function () {
                    return (
                        n(e),
                        function () {
                            n({});
                        }
                    );
                }, []);
            }
        },
        9122: function (e, n, t) {
            "use strict";
            t.r(n),
                t.d(n, {
                    __N_SSG: function () {
                        return O;
                    },
                    default: function () {
                        return y;
                    },
                });
            var r = t(5893),
                o = t(1799),
                i = t(7294),
                s = t(197);
            var a = i.forwardRef(function (e, n) {
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
                        d: "M12 15v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2zm10-10V7a4 4 0 00-8 0v4h8z",
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
                            d: "M8 11V7a4 4 0 118 0m-4 8v2m-6 4h12a2 2 0 002-2v-6a2 2 0 00-2-2H6a2 2 0 00-2 2v6a2 2 0 002 2z",
                        }),
                    );
                }),
                l = t(7161),
                u = t(3737),
                d = t(6896);
            var p = i.forwardRef(function (e, n) {
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
                        d: "M11 5H6a2 2 0 00-2 2v11a2 2 0 002 2h11a2 2 0 002-2v-5m-1.414-9.414a2 2 0 112.828 2.828L11.828 15H9v-2.828l8.586-8.586z",
                    }),
                );
            });
            var f = i.forwardRef(function (e, n) {
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
                            d: "M15 9a2 2 0 10-4 0v5a2 2 0 01-2 2h6m-6-4h4m8 0a9 9 0 11-18 0 9 9 0 0118 0z",
                        }),
                    );
                }),
                h = t(7556),
                g = t(9014),
                m = t(7065),
                v = t(8917),
                w = t(4951);
            var b = function (e) {
                    var n = (0, i.useState)(!0),
                        t = n[0],
                        o = n[1],
                        b = (0, i.useState)(0),
                        k = b[0],
                        x = b[1],
                        j = null;
                    return (
                        (function (e) {
                            var n = (0, i.useContext)(w.ZP).setTopBar;
                            (0, i.useEffect)(function () {
                                return (
                                    n(e),
                                    function () {
                                        n({});
                                    }
                                );
                            }, []);
                        })({
                            menu: [
                                {
                                    label: "File",
                                    items: [
                                        { label: "Download", href: "#", icon: s.Z },
                                        { label: "sep1", seperator: !0 },
                                        { label: "Lock", href: "#", icon: a },
                                        { label: "Unlock", href: "#", icon: c },
                                    ],
                                },
                                {
                                    label: "Database",
                                    items: [
                                        { label: "Audit", href: "#", icon: l.Z },
                                        { label: "Inspect", href: "#", icon: u.Z },
                                    ],
                                },
                                { label: "Template", items: [{ label: "View", href: "#", icon: d.Z }] },
                                { label: "Download", href: "#", icon: s.Z },
                            ],
                            selections: [
                                {
                                    id: 1,
                                    name: "Financial Year",
                                    description: "A short description of this single-select property",
                                    url: "#",
                                    color: "bg-orange-500",
                                    icon: p,
                                    type: "single",
                                    options: ["2001-2002", "2002-2003", "2004-2005", "2005-2006"],
                                },
                                {
                                    id: 2,
                                    name: "District",
                                    description: "A short description of this multi-select property",
                                    url: "#",
                                    color: "bg-red-700",
                                    icon: f,
                                    type: "multi",
                                    options: ["Birmingham", "Cornwall", "Manchester", "Bradford"],
                                },
                                {
                                    id: 3,
                                    name: "Management Team",
                                    description: "A short description of this single-select property",
                                    url: "#",
                                    color: "bg-yellow-500",
                                    icon: h.Z,
                                    type: "single",
                                    options: ["Marketing", "Company Directors", "Finance", "HR"],
                                },
                                {
                                    id: 4,
                                    name: "Other #1",
                                    description: "A short description of this property",
                                    url: "#",
                                    color: "bg-black",
                                    icon: g.Z,
                                    type: "single",
                                    options: ["Option 1", "Option 2", "Option 3"],
                                },
                                {
                                    id: 5,
                                    name: "Other #2",
                                    description: "A short description of this property",
                                    url: "#",
                                    color: "bg-black",
                                    icon: g.Z,
                                    type: "single",
                                    options: ["Option 1", "Option 2", "Option 3"],
                                },
                                {
                                    id: 6,
                                    name: "Other #3",
                                    description: "A short description of this property",
                                    url: "#",
                                    color: "bg-black",
                                    icon: g.Z,
                                    type: "single",
                                    options: ["Option 1", "Option 2", "Option 3"],
                                },
                                {
                                    id: 7,
                                    name: "Other #4",
                                    description: "A short description of this property",
                                    url: "#",
                                    color: "bg-black",
                                    icon: g.Z,
                                    type: "single",
                                    options: ["Option 1", "Option 2", "Option 3"],
                                },
                                {
                                    id: 8,
                                    name: "Other #5",
                                    description: "A short description of this property",
                                    url: "#",
                                    color: "bg-black",
                                    icon: g.Z,
                                    type: "single",
                                    options: ["Option 1", "Option 2", "Option 3"],
                                },
                            ],
                        }),
                        (0, i.useEffect)(
                            function () {
                                o(!0), x(0);
                            },
                            [e.id],
                        ),
                        (0, i.useEffect)(
                            function () {
                                return (
                                    t &&
                                        (j = setInterval(function () {
                                            x(function (e) {
                                                return e + 1;
                                            });
                                        }, 25)),
                                    function () {
                                        j && clearInterval(j);
                                    }
                                );
                            },
                            [t],
                        ),
                        (0, i.useEffect)(
                            function () {
                                k >= 100 && (o(!1), x(0));
                            },
                            [k],
                        ),
                        (0, r.jsx)("div", {
                            className: "az-report",
                            children: (0, r.jsx)(m.g, {
                                isLoading: t,
                                percentage: k,
                                children: (0, r.jsx)("iframe", {
                                    src: v.gt,
                                    width: "100%",
                                    height: "100%",
                                    frameBorder: "0",
                                }),
                            }),
                        })
                    );
                },
                k = t(1163),
                x = function (e) {
                    var n = (0, k.useRouter)(),
                        t = {
                            author: "",
                            database: "",
                            description: "",
                            id: parseInt(null === n || void 0 === n ? void 0 : n.query.id),
                            name: "",
                        };
                    return (0, r.jsx)("div", { className: "az-report-view", children: (0, r.jsx)(b, (0, o.Z)({}, t)) });
                },
                j = t(3777),
                O = !0,
                y = function () {
                    return (0, j.Z)({ title: "Report", compact: !0 }), (0, r.jsx)(x, {});
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

(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [805],
    {
        9450: function (e, n, r) {
            (window.__NEXT_P = window.__NEXT_P || []).push([
                "/imports/new/[stage]",
                function () {
                    return r(5906);
                },
            ]);
        },
        3274: function (e, n, r) {
            "use strict";
            r.d(n, {
                t: function () {
                    return t;
                },
            });
            var i = r(5893),
                t =
                    (r(7294),
                    function (e) {
                        return (0, i.jsx)("div", { className: "az-section-body", children: e.children });
                    });
        },
        3059: function (e, n, r) {
            "use strict";
            r.d(n, {
                Q: function () {
                    return s;
                },
            });
            var i = r(5893),
                t = (r(7294), r(9743)),
                s = function (e) {
                    return (0, i.jsxs)("div", {
                        className: "az-section-filter",
                        children: [
                            (0, i.jsx)("div", { children: (0, i.jsx)(t.Z, {}) }),
                            (0, i.jsx)("input", {
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
            var i = r(5893),
                t =
                    (r(7294),
                    function (e) {
                        return (0, i.jsx)("div", { className: "az-section-controls", children: e.children });
                    }),
                s = function (e) {
                    return (0, i.jsx)(i.Fragment, {
                        children: (0, i.jsxs)("div", {
                            className: "az-section-heading",
                            children: [
                                (0, i.jsx)("h3", { children: e.label }),
                                !!e.children && (0, i.jsx)(t, { children: e.children }),
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
            var i = r(5893),
                t = r(7294),
                s = function (e) {
                    var n,
                        r,
                        s = (0, t.useState)(!0),
                        l = (s[0], s[1], (0, t.useState)(0)),
                        a = l[0],
                        c = l[1],
                        d = e.children.length,
                        o = e.pageSize * a,
                        u = Math.min(e.pageSize * a + e.pageSize, d);
                    return (
                        (0, t.useEffect)(
                            function () {
                                c(0);
                            },
                            [d],
                        ),
                        (0, i.jsxs)("div", {
                            className: "az-table",
                            children: [
                                (0, i.jsxs)("table", {
                                    children: [
                                        (0, i.jsx)("thead", {
                                            children: (0, i.jsx)("tr", {
                                                children:
                                                    null === (n = e.columns) || void 0 === n
                                                        ? void 0
                                                        : n.map(function (e) {
                                                              return (0, i.jsx)("th", { children: e }, e);
                                                          }),
                                            }),
                                        }),
                                        (0, i.jsx)("tbody", { children: e.children.slice(o, u) }),
                                    ],
                                }),
                                !!d &&
                                    !(o < 2 && u === d) &&
                                    (0, i.jsxs)("nav", {
                                        children: [
                                            (0, i.jsx)("div", {
                                                children: (0, i.jsxs)("p", {
                                                    children: [
                                                        "Showing ",
                                                        (0, i.jsx)("strong", { children: o + 1 }),
                                                        " to ",
                                                        (0, i.jsx)("strong", { children: u }),
                                                        " of ",
                                                        (0, i.jsx)("strong", { children: d }),
                                                        " ",
                                                        null !== (r = e.label) && void 0 !== r ? r : "results",
                                                    ],
                                                }),
                                            }),
                                            (0, i.jsx)("div", {
                                                children: (0, i.jsxs)(i.Fragment, {
                                                    children: [
                                                        (0, i.jsx)("button", {
                                                            onClick: function () {
                                                                return c(a - 1);
                                                            },
                                                            disabled: o < 2,
                                                            children: "Previous",
                                                        }),
                                                        (0, i.jsx)("button", {
                                                            onClick: function () {
                                                                return c(a + 1);
                                                            },
                                                            disabled: u === d,
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
        70: function (e, n, r) {
            "use strict";
            r.d(n, {
                b: function () {
                    return u;
                },
            });
            var i = r(5893),
                t = r(7294);
            var s = t.forwardRef(function (e, n) {
                return t.createElement(
                    "svg",
                    Object.assign(
                        {
                            xmlns: "http://www.w3.org/2000/svg",
                            viewBox: "0 0 20 20",
                            fill: "currentColor",
                            "aria-hidden": "true",
                            ref: n,
                        },
                        e,
                    ),
                    t.createElement("path", {
                        fillRule: "evenodd",
                        d: "M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-3a1 1 0 00-.867.5 1 1 0 11-1.731-1A3 3 0 0113 8a3.001 3.001 0 01-2 2.83V11a1 1 0 11-2 0v-1a1 1 0 011-1 1 1 0 100-2zm0 8a1 1 0 100-2 1 1 0 000 2z",
                        clipRule: "evenodd",
                    }),
                );
            });
            var l = t.forwardRef(function (e, n) {
                return t.createElement(
                    "svg",
                    Object.assign(
                        {
                            xmlns: "http://www.w3.org/2000/svg",
                            viewBox: "0 0 20 20",
                            fill: "currentColor",
                            "aria-hidden": "true",
                            ref: n,
                        },
                        e,
                    ),
                    t.createElement("path", {
                        fillRule: "evenodd",
                        d: "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z",
                        clipRule: "evenodd",
                    }),
                );
            });
            var a = t.forwardRef(function (e, n) {
                return t.createElement(
                    "svg",
                    Object.assign(
                        {
                            xmlns: "http://www.w3.org/2000/svg",
                            viewBox: "0 0 20 20",
                            fill: "currentColor",
                            "aria-hidden": "true",
                            ref: n,
                        },
                        e,
                    ),
                    t.createElement("path", {
                        fillRule: "evenodd",
                        d: "M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z",
                        clipRule: "evenodd",
                    }),
                );
            });
            var c = t.forwardRef(function (e, n) {
                    return t.createElement(
                        "svg",
                        Object.assign(
                            {
                                xmlns: "http://www.w3.org/2000/svg",
                                viewBox: "0 0 20 20",
                                fill: "currentColor",
                                "aria-hidden": "true",
                                ref: n,
                            },
                            e,
                        ),
                        t.createElement("path", {
                            fillRule: "evenodd",
                            d: "M10 18a8 8 0 100-16 8 8 0 000 16zM8.707 7.293a1 1 0 00-1.414 1.414L8.586 10l-1.293 1.293a1 1 0 101.414 1.414L10 11.414l1.293 1.293a1 1 0 001.414-1.414L11.414 10l1.293-1.293a1 1 0 00-1.414-1.414L10 8.586 8.707 7.293z",
                            clipRule: "evenodd",
                        }),
                    );
                }),
                d = r(4184),
                o = r.n(d),
                u = function (e) {
                    return (0, i.jsx)("div", {
                        className: o()(["az-alert", "az-alert-".concat(e.variant)]),
                        children: (0, i.jsxs)("div", {
                            children: [
                                e.variant &&
                                    (0, i.jsxs)(i.Fragment, {
                                        children: [
                                            (0, i.jsx)("div", { children: "info" === e.variant && (0, i.jsx)(s, {}) }),
                                            (0, i.jsx)("div", {
                                                children: "success" === e.variant && (0, i.jsx)(l, {}),
                                            }),
                                            (0, i.jsx)("div", {
                                                children: "warning" === e.variant && (0, i.jsx)(a, {}),
                                            }),
                                            (0, i.jsx)("div", {
                                                children: "danger" === e.variant && (0, i.jsx)(c, {}),
                                            }),
                                        ],
                                    }),
                                (0, i.jsxs)("div", {
                                    children: [
                                        e.title && (0, i.jsx)("h3", { children: e.title }),
                                        (0, i.jsx)("div", { children: e.children }),
                                    ],
                                }),
                            ],
                        }),
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
            var i = r(4134),
                t = r(7294);
            function s(e) {
                var n = (0, t.useContext)(i.ZP).setLayout;
                (0, t.useEffect)(function () {
                    return (
                        n(e),
                        function () {
                            n({});
                        }
                    );
                }, []);
            }
        },
        5906: function (e, n, r) {
            "use strict";
            r.r(n),
                r.d(n, {
                    __N_SSG: function () {
                        return N;
                    },
                    default: function () {
                        return k;
                    },
                });
            var i = r(5893),
                t = r(1799),
                s = r(9396),
                l = r(70),
                a = r(7294);
            var c = a.forwardRef(function (e, n) {
                    return a.createElement(
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
                        a.createElement("path", {
                            strokeLinecap: "round",
                            strokeLinejoin: "round",
                            d: "M5 13l4 4L19 7",
                        }),
                    );
                }),
                d = r(3274),
                o = r(3059),
                u = r(67),
                h = r(8438),
                x = function (e) {
                    var n = (0, a.useState)(""),
                        r = n[0],
                        t = n[1],
                        s = e.properties.filter(function (e) {
                            var n;
                            return null === (n = e.id) || void 0 === n
                                ? void 0
                                : n.toLowerCase().includes(null === r || void 0 === r ? void 0 : r.toLowerCase());
                        });
                    return (0, i.jsxs)(i.Fragment, {
                        children: [
                            (0, i.jsx)(u.O, { label: "Properties", children: (0, i.jsx)(o.Q, { onChange: t }) }),
                            (0, i.jsxs)(d.t, {
                                children: [
                                    (0, i.jsx)(l.b, {
                                        variant: "warning",
                                        children: (0, i.jsxs)("p", {
                                            children: [
                                                "Please review the ",
                                                (0, i.jsx)("strong", { children: "suggested name" }),
                                                " for each field. You can also",
                                                " ",
                                                (0, i.jsx)("strong", { children: "exclude" }),
                                                " columns from the import by unselecting the row.",
                                            ],
                                        }),
                                    }),
                                    (0, i.jsx)(h.i, {
                                        columns: ["Heading", "Sample", "Unique Values", "Suggested Name", ""],
                                        pageSize: 50,
                                        label: "Imports",
                                        children: s.map(function (e) {
                                            return (0, i.jsxs)(
                                                "tr",
                                                {
                                                    children: [
                                                        (0, i.jsx)("td", {
                                                            children: (0, i.jsx)("p", { children: e.id }),
                                                        }),
                                                        (0, i.jsx)("td", {
                                                            children: (0, i.jsx)("select", {
                                                                children: e.values.map(function (e) {
                                                                    return (0,
                                                                    i.jsx)("option", { value: e, children: e }, e);
                                                                }),
                                                            }),
                                                        }),
                                                        (0, i.jsx)("td", { children: e.count }),
                                                        (0, i.jsx)("td", {
                                                            children: (0, i.jsx)("input", {
                                                                type: "text",
                                                                defaultValue: e.name,
                                                            }),
                                                        }),
                                                        (0, i.jsx)("td", {
                                                            children: (0, i.jsx)("input", {
                                                                type: "checkbox",
                                                                defaultChecked: !0,
                                                            }),
                                                        }),
                                                    ],
                                                },
                                                e.id,
                                            );
                                        }),
                                    }),
                                ],
                            }),
                        ],
                    });
                },
                p = function (e) {
                    var n = (0, a.useState)(""),
                        r = n[0],
                        t = n[1],
                        s = e.properties.filter(function (e) {
                            var n;
                            return null === (n = e.id) || void 0 === n
                                ? void 0
                                : n.toLowerCase().includes(null === r || void 0 === r ? void 0 : r.toLowerCase());
                        });
                    return (0, i.jsxs)(i.Fragment, {
                        children: [
                            (0, i.jsx)(u.O, { label: "Relationships", children: (0, i.jsx)(o.Q, { onChange: t }) }),
                            (0, i.jsxs)(d.t, {
                                children: [
                                    (0, i.jsxs)(l.b, {
                                        variant: "warning",
                                        children: [
                                            (0, i.jsxs)("p", {
                                                children: [
                                                    "Please define ",
                                                    (0, i.jsx)("strong", { children: "parent-child" }),
                                                    " relationships. Typical relationships include:",
                                                ],
                                            }),
                                            (0, i.jsxs)("ul", {
                                                children: [
                                                    (0, i.jsx)("li", { children: "Customer > Order > Order Item" }),
                                                    (0, i.jsx)("li", { children: "Country > Town > Street" }),
                                                ],
                                            }),
                                        ],
                                    }),
                                    (0, i.jsxs)(l.b, {
                                        variant: "info",
                                        children: [
                                            (0, i.jsxs)("p", {
                                                children: [
                                                    "We've automatically identified ",
                                                    (0, i.jsx)("strong", { children: "potential" }),
                                                    " relationships. You can override these suggestions:",
                                                ],
                                            }),
                                            (0, i.jsxs)("ul", {
                                                children: [
                                                    (0, i.jsx)("li", {
                                                        children: "Product Page ID is a parent of Product ID",
                                                    }),
                                                    (0, i.jsx)("li", {
                                                        children: "Product Type is a parent of Product ID",
                                                    }),
                                                    (0, i.jsx)("li", {
                                                        children: "Product Type Group is a parent of Product Type",
                                                    }),
                                                    (0, i.jsx)("li", { children: "Brand is a parent of Product ID" }),
                                                    (0, i.jsx)("li", { children: "Launch is a parent of Product ID" }),
                                                ],
                                            }),
                                            (0, i.jsx)("p", {
                                                children:
                                                    "These child names are generally associated with one parent only.",
                                            }),
                                        ],
                                    }),
                                    (0, i.jsx)(h.i, {
                                        columns: ["Parent", "Child", ""],
                                        pageSize: 50,
                                        label: "Imports",
                                        children: s.map(function (n) {
                                            var r;
                                            return (0, i.jsxs)(
                                                "tr",
                                                {
                                                    children: [
                                                        (0, i.jsx)("td", {
                                                            children: (0, i.jsx)("p", { children: n.name }),
                                                        }),
                                                        (0, i.jsx)("td", {
                                                            children: (0, i.jsxs)("select", {
                                                                defaultValue: n.child,
                                                                children: [
                                                                    (0, i.jsx)("option", { value: void 0 }),
                                                                    e.properties.map(function (e) {
                                                                        return (0,
                                                                        i.jsx)("option", { value: e.name, children: e.name }, e.name);
                                                                    }),
                                                                ],
                                                            }),
                                                        }),
                                                        (0, i.jsx)("td", {
                                                            children:
                                                                n.child &&
                                                                (0, i.jsxs)(i.Fragment, {
                                                                    children: [
                                                                        "e.g. ",
                                                                        n.child,
                                                                        " ",
                                                                        (0, i.jsx)("i", {
                                                                            children:
                                                                                null ===
                                                                                    (r = e.properties.find(function (
                                                                                        e,
                                                                                    ) {
                                                                                        return e.name === n.child;
                                                                                    })) || void 0 === r
                                                                                    ? void 0
                                                                                    : r.values[0],
                                                                        }),
                                                                        " belongs to",
                                                                        " ",
                                                                        n.name,
                                                                        " ",
                                                                        (0, i.jsx)("i", { children: n.values[0] }),
                                                                    ],
                                                                }),
                                                        }),
                                                    ],
                                                },
                                                n.id,
                                            );
                                        }),
                                    }),
                                ],
                            }),
                        ],
                    });
                },
                j = r(9953),
                v = r(5812),
                f = r(1163),
                m = function () {
                    return (0, i.jsx)("div", {
                        className: "step-seperator",
                        children: (0, i.jsx)("svg", {
                            viewBox: "0 0 12 82",
                            fill: "none",
                            preserveAspectRatio: "none",
                            children: (0, i.jsx)("path", {
                                d: "M0.5 0V31L10.5 41L0.5 51V82",
                                stroke: "currentcolor",
                                vectorEffect: "non-scaling-stroke",
                            }),
                        }),
                    });
                },
                g = function (e) {
                    var n = j.ly.map(function (n) {
                        return (0,
                        s.Z)((0, t.Z)({}, n), { status: (n.id < e.step ? "complete" : n.id === e.step && "current") || (n.id > e.step && "upcoming") || "" });
                    });
                    return (0, i.jsx)("nav", {
                        className: "az-import-wizard-progress",
                        children: (0, i.jsx)("ol", {
                            children: n.map(function (n, r) {
                                return (0, i.jsx)(
                                    "li",
                                    {
                                        children: (0, i.jsxs)("div", {
                                            className: n.status,
                                            children: [
                                                (0, i.jsxs)("div", {
                                                    onClick: function () {
                                                        n.id < e.step && e.onClick(r + 1);
                                                    },
                                                    children: [
                                                        (0, i.jsx)("span", {}),
                                                        (0, i.jsxs)("span", {
                                                            children: [
                                                                (0, i.jsx)("span", {
                                                                    children: (0, i.jsx)("span", {
                                                                        children: (0, i.jsx)("span", {
                                                                            children:
                                                                                "complete" === n.status
                                                                                    ? (0, i.jsx)(c, {})
                                                                                    : n.id,
                                                                        }),
                                                                    }),
                                                                }),
                                                                (0, i.jsxs)("span", {
                                                                    children: [
                                                                        (0, i.jsx)("span", { children: n.name }),
                                                                        (0, i.jsx)("span", { children: n.description }),
                                                                    ],
                                                                }),
                                                            ],
                                                        }),
                                                    ],
                                                }),
                                                0 !== r ? (0, i.jsx)(m, {}) : null,
                                            ],
                                        }),
                                    },
                                    n.id,
                                );
                            }),
                        }),
                    });
                },
                w = function (e) {
                    return (0, i.jsx)("div", {
                        className: "az-import-wizard-pagination",
                        children: (0, i.jsxs)("div", {
                            children: [
                                1 !== e.step &&
                                    4 !== e.step &&
                                    (0, i.jsx)("button", {
                                        className: "az-wizard-button-back",
                                        onClick: e.onBack,
                                        children: "Back",
                                    }),
                                (0, i.jsx)("button", {
                                    className: "az-wizard-button-next",
                                    onClick: e.onNext,
                                    children: 4 !== e.step ? "Next" : "Finish",
                                }),
                            ],
                        }),
                    });
                },
                b = function (e) {
                    var n = (0, f.useRouter)();
                    return (0, i.jsxs)("div", {
                        className: "az-import-wizard",
                        children: [
                            (0, i.jsx)(g, {
                                step: e.step,
                                onClick: function (e) {
                                    return null === n || void 0 === n ? void 0 : n.push("/imports/new/" + e);
                                },
                            }),
                            (0, i.jsxs)("div", {
                                className: "az-import-wizard-main",
                                children: [
                                    1 === e.step &&
                                        (0, i.jsx)(i.Fragment, { children: (0, i.jsx)(x, { properties: v.aK }) }),
                                    2 === e.step &&
                                        (0, i.jsx)(i.Fragment, { children: (0, i.jsx)(p, { properties: v.aK }) }),
                                    3 === e.step &&
                                        (0, i.jsxs)(i.Fragment, {
                                            children: [
                                                (0, i.jsx)(u.O, { label: "Stage 3", children: (0, i.jsx)(o.Q, {}) }),
                                                (0, i.jsxs)(l.b, {
                                                    variant: "warning",
                                                    children: [
                                                        (0, i.jsxs)("p", {
                                                            children: [
                                                                "For the purposes of this ",
                                                                (0, i.jsx)("strong", { children: "prototype" }),
                                                                ", this is a placeholder for another stage.",
                                                            ],
                                                        }),
                                                        (0, i.jsxs)("p", {
                                                            children: [
                                                                "If another stage is required, it will ",
                                                                (0, i.jsx)("strong", { children: "appear here" }),
                                                                ".",
                                                            ],
                                                        }),
                                                    ],
                                                }),
                                            ],
                                        }),
                                    4 === e.step &&
                                        (0, i.jsx)(i.Fragment, {
                                            children: (0, i.jsx)(l.b, {
                                                variant: "success",
                                                title: "Complete!",
                                                children: (0, i.jsx)("p", {
                                                    children: "The import has been successfully completed.",
                                                }),
                                            }),
                                        }),
                                    (0, i.jsx)(w, {
                                        step: e.step,
                                        onBack: function () {
                                            return null === n || void 0 === n
                                                ? void 0
                                                : n.push("/imports/new/" + (e.step - 1));
                                        },
                                        onNext: function () {
                                            e.step + 1 === 5
                                                ? null === n || void 0 === n || n.push("/imports")
                                                : null === n || void 0 === n || n.push("/imports/new/" + (e.step + 1));
                                        },
                                    }),
                                ],
                            }),
                        ],
                    });
                },
                z = function (e) {
                    return (0, i.jsx)("div", {
                        className: "az-import-wizard-view",
                        children: (0, i.jsx)(b, (0, t.Z)({}, e)),
                    });
                },
                C = r(3777),
                N = !0,
                k = function () {
                    var e = (0, f.useRouter)();
                    return (
                        (0, C.Z)({ title: "Import Wizard" }),
                        (0, i.jsx)(z, { step: parseInt(null === e || void 0 === e ? void 0 : e.query.stage) })
                    );
                };
        },
    },
    function (e) {
        e.O(0, [774, 888, 179], function () {
            return (n = 9450), e((e.s = n));
            var n;
        });
        var n = e.O();
        _N_E = n;
    },
]);

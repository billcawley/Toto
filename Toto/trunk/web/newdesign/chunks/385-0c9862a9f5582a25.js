"use strict";
(self.webpackChunk_N_E = self.webpackChunk_N_E || []).push([
    [385],
    {
        3274: function (e, n, r) {
            r.d(n, {
                t: function () {
                    return a;
                },
            });
            var t = r(5893),
                a =
                    (r(7294),
                        function (e) {
                            return (0, t.jsx)("div", {className: "az-section-body", children: e.children});
                        });
        },
        3059: function (e, n, r) {
            r.d(n, {
                Q: function () {
                    return i;
                },
            });
            var t = r(5893),
                a = (r(7294), r(9743)),
                i = function (e) {
                    return (0, t.jsxs)("div", {
                        className: "az-section-filter",
                        children: [
                            (0, t.jsx)("div", {children: (0, t.jsx)(a.Z, {})}),
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
            r.d(n, {
                O: function () {
                    return i;
                },
            });
            var t = r(5893),
                a =
                    (r(7294),
                        function (e) {
                            return (0, t.jsx)("div", {className: "az-section-controls", children: e.children});
                        }),
                i = function (e) {
                    return (0, t.jsx)(t.Fragment, {
                        children: (0, t.jsxs)("div", {
                            className: "az-section-heading",
                            children: [
                                (0, t.jsx)("h3", {children: e.label}),
                                !!e.children && (0, t.jsx)(a, {children: e.children}),
                            ],
                        }),
                    });
                };
        },
        3341: function (e, n, r) {
            r.d(n, {
                K: function () {
                    return c;
                },
            });
            var t = r(5893),
                a = r(7294);
            var i = a.forwardRef(function (e, n) {
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
                        d: "M4 6h16M4 10h16M4 14h16M4 18h16",
                    }),
                );
            });
            var s = a.forwardRef(function (e, n) {
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
                            d: "M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z",
                        }),
                    );
                }),
                l = r(4184),
                o = r.n(l),
                c = function (e) {
                    return (0, t.jsx)("div", {
                        className: "az-section-view",
                        children: (0, t.jsxs)("span", {
                            children: [
                                (0, t.jsx)("button", {
                                    onClick: function () {
                                        return e.onChange("list");
                                    },
                                    className: o()({selected: "list" === e.view}),
                                    children: (0, t.jsx)(i, {}),
                                }),
                                (0, t.jsx)("button", {
                                    onClick: function () {
                                        return e.onChange("grid");
                                    },
                                    className: o()({selected: "grid" === e.view}),
                                    children: (0, t.jsx)(s, {}),
                                }),
                            ],
                        }),
                    });
                };
        },
        8438: function (e, n, r) {
            r.d(n, {
                i: function () {
                    return i;
                },
            });
            var t = r(5893),
                a = r(7294),
                i = function (e) {
                    var n,
                        r,
                        i = (0, a.useState)(!0),
                        s = (i[0], i[1], (0, a.useState)(0)),
                        l = s[0],
                        o = s[1],
                        c = e.children.length,
                        d = e.pageSize * l,
                        u = Math.min(e.pageSize * l + e.pageSize, c);
                    return (
                        (0, a.useEffect)(
                            function () {
                                o(0);
                            },
                            [c],
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
                                                                return (0, t.jsx)("th", {children: e}, e);
                                                            }),
                                                }),
                                            }),
                                            (0, t.jsx)("tbody", {children: e.children.slice(d, u)}),
                                        ],
                                    }),
                                    !!c &&
                                    !(d < 2 && u === c) &&
                                    (0, t.jsxs)("nav", {
                                        children: [
                                            (0, t.jsx)("div", {
                                                children: (0, t.jsxs)("p", {
                                                    children: [
                                                        "Showing ",
                                                        (0, t.jsx)("strong", {children: d + 1}),
                                                        " to ",
                                                        (0, t.jsx)("strong", {children: u}),
                                                        " of ",
                                                        (0, t.jsx)("strong", {children: c}),
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
                                                                return o(l - 1);
                                                            },
                                                            disabled: d < 2,
                                                            children: "Previous",
                                                        }),
                                                        (0, t.jsx)("button", {
                                                            onClick: function () {
                                                                return o(l + 1);
                                                            },
                                                            disabled: u === c,
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
        7065: function (e, n, r) {
            r.d(n, {
                g: function () {
                    return o;
                },
            });
            var t = r(5893),
                a = r(7294);
            var i = a.forwardRef(function (e, n) {
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
                            d: "M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z",
                        }),
                    );
                }),
                s = r(8917),
                l = function () {
                    return (0, t.jsx)("img", {className: "az-spinner", src: s.lY});
                },
                o = function (e) {
                    var n,
                        r = (0, a.useState)(!1),
                        s = r[0],
                        o = r[1];
                    return e.isLoading
                        ? (0, t.jsx)(t.Fragment, {
                            children: (0, t.jsxs)("div", {
                                className: "az-loading",
                                children: [
                                    (0, t.jsxs)("div", {
                                        className: "az-loading-info",
                                        children: [
                                            (0, t.jsx)(l, {}),
                                            !!e.percentage &&
                                            (0, t.jsxs)("span", {
//                                                      children: [e.percentage <= 100 ? e.percentage : 100, "%"],
                                                children: "",
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
                                                        e.onShowLog && e.onShowLog(!s), o(!s);
                                                    },
                                                    children: [
                                                        (0, t.jsx)(i, {}),
                                                        " ",
                                                        s ? "Hide" : "Show",
                                                        " detail",
                                                    ],
                                                }),
                                            }),
                                            s &&
                                            (0, t.jsx)("div", {
                                                children: (0, t.jsx)("ul", {
                                                    children: e.log.map(function (e) {
                                                        return (0, t.jsx)("li", {children: e}, e);
                                                    }),
                                                }),
                                            }),
                                        ],
                                    }),
                                ],
                            }),
                        })
                        : (0, t.jsx)(t.Fragment, {children: e.children});
                };
        },
        378: function (e, n, r) {
            r.d(n, {
                o: function () {
                    return T;
                },
            });
            var t = r(5893),
                a = r(7294);
            var i = a.forwardRef(function (e, n) {
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
                        d: "M3 19v-8.93a2 2 0 01.89-1.664l7-4.666a2 2 0 012.22 0l7 4.666A2 2 0 0121 10.07V19M3 19a2 2 0 002 2h14a2 2 0 002-2M3 19l6.75-4.5M21 19l-6.75-4.5M3 10l6.75 4.5M21 10l-6.75 4.5m0 0l-1.14.76a2 2 0 01-2.22 0l-1.14-.76",
                    }),
                );
            });
            var s = a.forwardRef(function (e, n) {
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
                            d: "M8 12h.01M12 12h.01M16 12h.01M21 12c0 4.418-4.03 8-9 8a9.863 9.863 0 01-4.255-.949L3 20l1.395-3.72C3.512 15.042 3 13.574 3 12c0-4.418 4.03-8 9-8s9 3.582 9 8z",
                        }),
                    );
                }),
                l = r(1575),
                o = r(197),
                c = r(8945),
                d = r(8917),
                u = r(2837),
                h = r(1355),
                f = r(7216);
            var x = a.forwardRef(function (e, n) {
                return a.createElement(
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
                    a.createElement("path", {
                        fillRule: "evenodd",
                        d: "M10 3a1 1 0 01.707.293l3 3a1 1 0 01-1.414 1.414L10 5.414 7.707 7.707a1 1 0 01-1.414-1.414l3-3A1 1 0 0110 3zm-3.707 9.293a1 1 0 011.414 0L10 14.586l2.293-2.293a1 1 0 011.414 1.414l-3 3a1 1 0 01-1.414 0l-3-3a1 1 0 010-1.414z",
                        clipRule: "evenodd",
                    }),
                );
            });
            var v = a.forwardRef(function (e, n) {
                    return a.createElement(
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
                        a.createElement("path", {
                            fillRule: "evenodd",
                            d: "M16.707 5.293a1 1 0 010 1.414l-8 8a1 1 0 01-1.414 0l-4-4a1 1 0 011.414-1.414L8 12.586l7.293-7.293a1 1 0 011.414 0z",
                            clipRule: "evenodd",
                        }),
                    );
                }),
                j = r(1147),
                m = r(4184),
                p = r.n(m),
                g = function (e) {
                    var n = (0, a.useState)(""),
                        r = n[0],
                        i = n[1],
                        s = (0, a.useState)(),
                        l = s[0],
                        o = s[1],
                        c = e.items.filter(function (e) {
                            var n;
                            return null === (n = e.name) || void 0 === n
                                ? void 0
                                : n.toLowerCase().includes(null === r || void 0 === r ? void 0 : r.toLowerCase());
                        });
                    return (0, t.jsxs)(j.h, {
                        className: p()(["az-combobox", {error: e.error}]),
                        as: "div",
                        value: l,
                        onChange: function (n) {
                            o(n), e.onChange(n);
                        },
                        children: [
                            e.label && (0, t.jsx)(j.h.Label, {children: e.label}),
                            (0, t.jsxs)("div", {
                                children: [
                                    (0, t.jsx)(j.h.Input, {
                                        onChange: function (e) {
                                            return i(e.target.value);
                                        },
                                        displayValue: function (e) {
                                            return null === e || void 0 === e ? void 0 : e.name;
                                        },
                                    }),
                                    (0, t.jsx)(j.h.Button, {children: (0, t.jsx)(x, {})}),
                                    c.length > 0 &&
                                    (0, t.jsx)(j.h.Options, {
                                        children: c.map(function (e) {
                                            return (0, t.jsx)(
                                                j.h.Option,
                                                {
                                                    value: e,
                                                    className: function (e) {
                                                        var n = e.active;
                                                        return p()({active: n});
                                                    },
                                                    children: function (n) {
                                                        var r = n.active,
                                                            a = n.selected;
                                                        return (0, t.jsxs)(t.Fragment, {
                                                            children: [
                                                                (0, t.jsx)("span", {
                                                                    className: p()("item-name", {selected: a}),
                                                                    children: e.name,
                                                                }),
                                                                a &&
                                                                (0, t.jsx)("span", {
                                                                    className: p()("item-icon", {active: r}),
                                                                    children: (0, t.jsx)(v, {}),
                                                                }),
                                                            ],
                                                        });
                                                    },
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
                w = r(7065),
                b = r(5812),
                z = r(9790),
                C = r(1163),
                N = function (e) {
                    var n = (0, C.useRouter)(),
                        r = (0, a.useState)(!1),
                        i = r[0],
                        s = r[1],
                        l = (0, a.useState)(!1),
                        o = l[0],
                        c = l[1],
                        d = (0, a.useState)(0),
                        u = d[0],
                        h = d[1],
                        f = (0, a.useState)([]),
                        x = f[0],
                        v = f[1],
                        j = (0, a.useState)(null),
                        m = j[0],
                        N = j[1],
                        k = (0, a.useState)(null),
                        S = k[0],
                        L = k[1],
                        M = null,
                        E = null;
                    return (
                        (0, a.useEffect)(
                            function () {
                                return (
                                    i &&
                                    ((M = setInterval(function () {
                                        h(function (e) {
                                            return e + 1;
                                        });
                                    }, 50)),
                                        (E = setInterval(function () {
                                            v(function (e) {
                                                return e.concat("This is an example log line no." + e.length);
                                            });
                                        }, 200))),
                                        function () {
                                            M && clearInterval(M), E && clearInterval(E);
                                        }
                                );
                            },
                            [i],
                        ),
                            (0, a.useEffect)(
                                function () {
                                    u >= 1000 &&
                                    (clearInterval(M),
                                        clearInterval(E),
                                    null === n || void 0 === n || n.push("/imports/new/1"));
                                },
                                [u],
                            ),
                            (0, a.useEffect)(
                                function () {
                                    m && S && s(!0);
                                },
                                [m, S],
                            ),
                            (0, t.jsx)("div", {
                                className: "az-fileupload",
                                children: (0, t.jsx)("div", {
                                    children: (0, t.jsx)("form", {
                                        method: "POST",
                                        action: "/api/ManageDatabases",
                                        enctype: "multipart/form-data",
                                        id : "uploadForm",
                                        children: (0, t.jsx)("div", {
                                            className: p()(["az-file-upload-control", {loading: i, logging: o}]),
                                                children: (0, t.jsxs)("div", {
                                                    children: [
                                                        (0, t.jsx)("div", {
                                                            className: "az-file-upload-database",
                                                            children: (0, t.jsx)(g, {
                                                                items: (0, z.Y)(b.UA, "name"),
                                                                label: "Database",
                                                                name: "database",
                                                                onChange: N,
                                                            }),
                                                        }),
                                                        (0, t.jsxs)("div", {
                                                            className: "az-form-seperator",
                                                            children: [
                                                                (0, t.jsx)("div", {children: (0, t.jsx)("div", {})}),
                                                                (0, t.jsx)("div", {
                                                                    children: (0, t.jsx)("span", {children: "and"}),
                                                                }),
                                                            ],
                                                        }),
                                                        (0, t.jsxs)("div", {
                                                            className: "az-file-upload-file",
                                                            children: [
                                                                (0, t.jsx)("div", {
                                                                    children: (0, t.jsxs)("label", {
                                                                        htmlFor: "az-file-upload",
                                                                        children: [
                                                                            (0, t.jsx)("span", {
                                                                                id : "selector",
                                                                                children: "Select file",
                                                                            }),
                                                                            (0, t.jsx)("input", {
                                                                                id: "az-file-upload",
                                                                                name: "uploadFile",
                                                                                type: "file",
                                                                                onChange: function (e) {
                                                                                    document.getElementById("selector").innerHTML = e.target.value.replace(/^.*[\\\/]/, "")
                                                                                },
                                                                            }),
                                                                        ],
                                                                    }),
                                                                }),
                                                                (0, t.jsx)("p", {
                                                                    children:
                                                                        [(0, t.jsxs)("div", {
                                                                                className: "az-section-buttons",
                                                                                children: (0, t.jsx)("button", {
                                                                                    value: "something",
                                                                                    onClick: function (e) {
                                                                                        L(document.getElementById("selector").innerHTML);
                                                                                        document.getElementById("databaseHidden").value = document.getElementById("headlessui-combobox-input-:rg:").value;
                                                                                        document.getElementById("uploadForm").submit()
                                                                                    },
                                                                                    children: "Upload",
                                                                                }),
                                                                            },
                                                                        ),
                                                                            (0, t.jsx)("input", {
                                                                                id: "databaseHidden",
                                                                                name: "database",
                                                                                type: "hidden"
                                                                            }),
                                                                            (0, t.jsx)("input", {
                                                                                name: "newdesign",
                                                                                type: "hidden",
                                                                                value: "true"
                                                                            }),
                                                                        ]
                                                                })

                                                            ],
                                                        }),
                                                    ],
                                            }),
                                        }),
                                    }),
                                }),
                            })
                    );
                },
                k = function (e) {
                    var n = (0, a.useState)(!1),
                        r = n[0],
                        i = n[1];
                    return (
                        (0, a.useEffect)(
                            function () {
                                i(e.show);
                            },
                            [e.show],
                        ),
                            (0, t.jsx)(h.u.Root, {
                                show: r,
                                as: a.Fragment,
                                children: (0, t.jsxs)(f.V, {
                                    as: "div",
                                    className: "az-fileupload-modal",
                                    onClose: e.onClose,
                                    children: [
                                        (0, t.jsx)(h.u.Child, {
                                            as: a.Fragment,
                                            enter: "ease-out duration-300",
                                            enterFrom: "opacity-0",
                                            enterTo: "opacity-100",
                                            leave: "ease-in duration-200",
                                            leaveFrom: "opacity-100",
                                            leaveTo: "opacity-0",
                                            children: (0, t.jsx)("div", {className: "az-fileupload-modal-background"}),
                                        }),
                                        (0, t.jsx)("div", {
                                            className: "az-fileupload-modal-container",
                                            children: (0, t.jsx)("div", {
                                                children: (0, t.jsx)(h.u.Child, {
                                                    as: a.Fragment,
                                                    enter: "ease-out duration-300",
                                                    enterFrom: "opacity-0 translate-y-4 translate-y-0 scale-95",
                                                    enterTo: "opacity-100 translate-y-0 scale-100",
                                                    leave: "ease-in duration-200",
                                                    leaveFrom: "opacity-100 translate-y-0 scale-100",
                                                    leaveTo: "opacity-0 translate-y-4 translate-y-0 scale-95",
                                                    children: (0, t.jsx)(f.V.Panel, {children: (0, t.jsx)(N, {})}),
                                                }),
                                            }),
                                        }),
                                    ],
                                }),
                            })
                    );
                },
                S = r(1799),
                L = r(1664),
                M = r.n(L),
                E = r(381),
                y = r.n(E),
                F = function (e) {
                    return (0, t.jsxs)("div", {
                        className: "az-import-card",
                        children: [
                            (0, t.jsx)(M(), {
                                href: "/api/DownloadFile?uploadRecordId=" + e.id,
                                children: (0, t.jsxs)("a", {
                                    children: [
                                        (0, t.jsxs)("div", {
                                            className: "az-card-data",
                                            children: [
                                                (0, t.jsx)("h3", {children: e.filename}),
                                                (0, t.jsxs)("p", {children: [e.database, " by ", e.user]}),
                                                (0, t.jsx)("p", {children: y().unix(e.date).format(d.vc)}),
                                            ],
                                        }),
                                        (0, t.jsx)("div", {className: "az-card-icon", children: (0, t.jsx)(l.Z, {})}),
                                    ],
                                }),
                            }),
                            (0, t.jsxs)("div", {
                                className: "az-card-options",
                                children: [
                                    (0, t.jsx)("div", {
                                        children: (0, t.jsx)(M(), {
                                            href: "/api/ImportResults?urid=" + e.id,
                                            children: (0, t.jsxs)("a", {
                                                children: [
                                                    (0, t.jsx)(i, {}),
                                                    (0, t.jsx)("span", {children: "Results"}),
                                                ],
                                            }),
                                        }),
                                    }),
                                    (0, t.jsx)("div", {
                                        children: (0, t.jsxs)("a", {
                                            href: "/api/DownloadFile?uploadRecordId=" + e.id,
                                            children: [
                                                (0, t.jsx)(o.Z, {}),
                                                (0, t.jsx)("span", {children: "Download"}),
                                            ],
                                        }),
                                    }),
                                    (0, t.jsx)("div", {
                                        className: "az-card-actions",
                                        children: (0, t.jsx)(u.e, {
                                            items: [
                                                //{ label: "Comment", href: "#", icon: s },
                                                {label: "sep1", seperator: !0},
                                                {
                                                    label: "Delete",
                                                    href: "/api/ManageDatabases?deleteUploadRecordId=" + e.id,
                                                    icon: c.Z
                                                },
                                            ],
                                        }),
                                    }),
                                ],
                            }),
                        ],
                    });
                },
                R = function (e) {
                    return (0, t.jsx)("div", {
                        className: "az-import-cards",
                        children: e.imports.map(function (e) {
                            return (0, t.jsx)(F, (0, S.Z)({}, e), e.id);
                        }),
                    });
                },
                O = r(3274),
                Z = function (e) {
                    return (0, t.jsx)("div", {className: "az-section-buttons", children: e.children});
                },
                B = r(3059),
                I = r(67),
                D = r(3341),
                V = r(8438),
                T = function (e) {
                    var n = (0, a.useState)(""),
                        r = n[0],
                        h = n[1],
                        f = (0, a.useState)("list"),
                        x = f[0],
                        v = f[1],
                        j = (0, a.useState)(!1),
                        m = j[0],
                        p = j[1],
                        g = e.imports.filter(function (e) {
                            var n;
                            return null === (n = e.filename) || void 0 === n
                                ? void 0
                                : n.toLowerCase().includes(null === r || void 0 === r ? void 0 : r.toLowerCase());
                        });
                    return (0, t.jsxs)(t.Fragment, {
                        children: [
                            (0, t.jsxs)(I.O, {
                                label: "Imports",
                                children: [
                                    (0, t.jsx)(Z, {
                                        children: (0, t.jsxs)("button", {
                                            onClick: function () {
                                                return p(!0);
                                            },
                                            children: [(0, t.jsx)(l.Z, {}), " New"],
                                        }),
                                    }),
                                    (0, t.jsx)(B.Q, {onChange: h}),
                                    (0, t.jsx)(D.K, {onChange: v, view: x}),
                                ],
                            }),
                            importError ? (            (0, t.jsxs)("div", {
                                    className: "az-alert az-alert-error",
                                    children: (0, t.jsxs)("div", {
                                        children: [
                                            (0, t.jsxs)("div", {
                                                children: (0, t.jsxs)("p", {
                                                        children: importError
                                                    }
                                                )
                                            })
                                        ]
                                    })
                                })
                            ) : importWarning ? (            (0, t.jsxs)("div", {
                                    className: "az-alert az-alert-warning",
                                    children: (0, t.jsxs)("div", {
                                        children: [
                                            (0, t.jsxs)("div", {
                                                children: (0, t.jsxs)("p", {
                                                        children: importWarning
                                                    }
                                                )
                                            })
                                        ]
                                    })
                                })
                            ) : "",
                            (0, t.jsxs)(O.t, {
                                children: [
                                    "list" === x &&
                                    (0, t.jsx)(V.i, {
                                        columns: ["File Name", "Database", "User", "Date", ""],
                                        pageSize: e.pageSize,
                                        label: "Imports",
                                        children: (0, z.Y)(g, "date")
                                            .reverse()
                                            .map(function (e) {
                                                return (0, t.jsxs)(
                                                    "tr",
                                                    {
                                                        children: [
                                                            (0, t.jsx)("td", {
                                                                className: "full",
                                                                children: (0, t.jsx)("div", {
                                                                    children: (0, t.jsx)(M(), {
                                                                        href: "/api/DownloadFile?uploadRecordId=".concat(e.id),
                                                                        children: (0, t.jsxs)("a", {
                                                                            children: [
                                                                                (0, t.jsx)(l.Z, {}),
                                                                                (0, t.jsx)("p", {
                                                                                    children: e.filename,
                                                                                }),
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
                                                            (0, t.jsx)("td", {children: e.user}),
                                                            (0, t.jsx)("td", {
                                                                children: y().unix(e.date).format(d.vc),
                                                            }),
                                                            (0, t.jsx)("td", {
                                                                children: (0, t.jsx)(u.e, {
                                                                    items: [
                                                                        {
                                                                            label: "Results",
                                                                            href: "/api/ImportResults?urid=" + e.id,
                                                                            icon: i
                                                                        },
                                                                        {
                                                                            label: "Download",
                                                                            href: "/api/DownloadFile?uploadRecordId=".concat(e.id),
                                                                            icon: o.Z,
                                                                        },
                                                                        //{ label: "Comment", href: "#", icon: s },
                                                                        {label: "sep1", seperator: !0},
                                                                        {
                                                                            label: "Delete",
                                                                            href: "/api/ManageDatabases?deleteUploadRecordId=" + e.id,
                                                                            icon: c.Z
                                                                        },
                                                                    ],
                                                                }),
                                                            }),
                                                        ],
                                                    },
                                                    e.id,
                                                );
                                            }),
                                    }),
                                    "grid" === x && (0, t.jsx)(R, {imports: (0, z.Y)(g, "name")}),
                                ],
                            }),
                            (0, t.jsx)(k, {
                                show: m,
                                onClose: function () {
                                    return p(!1);
                                },
                            }),
                        ],
                    });
                };
        },
        3777: function (e, n, r) {
            r.d(n, {
                Z: function () {
                    return i;
                },
            });
            var t = r(4134),
                a = r(7294);

            function i(e) {
                var n = (0, a.useContext)(t.ZP).setLayout;
                (0, a.useEffect)(function () {
                    return (
                        n(e),
                            function () {
                                n({});
                            }
                    );
                }, []);
            }
        },
    },
]);

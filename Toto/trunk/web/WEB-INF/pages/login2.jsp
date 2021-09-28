<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Login - Azquo</title>
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bulma@0.9.3/css/bulma.min.css">
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/font-awesome/4.4.0/css/font-awesome.min.css">
</head>
<body>
<section class="section">
    <section class="hero ">
        <div class="hero-body">
            <div class="container">
                <div class="columns is-centered">
                    <div class="column is-5-tablet is-4-desktop is-3-widescreen">
                        <h1 class="title">Login to Azquo</h1>
                        <form action="/api/Login" class="box">
                            <div class="field">
                                <label class="label">Email/Username</label>
                                <div class="control has-icons-left">
                                    <input type="text" class="input"  value="${userEmail}" name="user" required>
                                    <span class="icon is-small is-left">
                  <i class="fa fa-envelope"></i>
                </span>
                                </div>
                            </div>
                            <div class="field">
                                <label class="label">Password</label>
                                <div class="control has-icons-left">
                                    <input type="password" placeholder="" class="input" name="password" required>
                                    <span class="icon is-small is-left">
                  <i class="fa fa-lock"></i>
                </span>
                                </div>
                            </div>
                            <c:if test="${not empty error}">
                                <p class="help is-danger">${error}</p><br/>
                            </c:if>
                            <div class="field">
                                <button class="button is-success">
                                    Login
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </section>
</section>


</body>
</html>
VERSION 5.00
Begin {C62A69F0-16DC-11CE-9E98-00AA00574A4F} LogonForm 
   Caption         =   "Log on to Azquo database"
   ClientHeight    =   2025
   ClientLeft      =   45
   ClientTop       =   375
   ClientWidth     =   3795
   OleObjectBlob   =   "LogonForm.frx":0000
   StartUpPosition =   1  'CenterOwner
End
Attribute VB_Name = "LogonForm"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False


Private Sub LogonSubmit_Click()
    azConnectionId = "aborted"
    
    Dim params As String
    'test code....
    If UserName = "" And Password = "" Then
       UserName = "demo@user.com"
       Password = "password"
    End If
     params = "useremail=" & UserName & "&password=" & Password & "&database=" & Database
    azConnectionId = AzquoPost1("Login", params)
    If (azConnectionId = "false") Then
       azConnectionId = ""
    End If
    If (azConnectionId > "") Then
       LogonForm.Hide
    Else
       MsgBox ("Please try again")
    End If
End Sub

Private Sub UserForm_Activate()
   If rangeExists("az_Logon") Then
      UserName = Range("az_Logon")
   End If
   If rangeExists("az_Password") Then
      Password = Range("az_Password")
   End If
   If rangeExists("az_Database") Then
      Database = Range("az_Database")
   End If
   If UserName > "" And Password > "" And Database > "" Then
        params = "useremail=" & UserName & "&password=" & Password & "&database=" & Database
        azConnectionId = AzquoPost1("Login", params)
        If azConnectionId > "" Then
          LogonForm.Hide
        End If
   End If

End Sub


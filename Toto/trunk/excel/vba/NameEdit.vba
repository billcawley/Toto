VERSION 5.00
Begin {C62A69F0-16DC-11CE-9E98-00AA00574A4F} NameEdit 
   Caption         =   "Edit Name"
   ClientHeight    =   3390
   ClientLeft      =   45
   ClientTop       =   345
   ClientWidth     =   6240
   OleObjectBlob   =   "NameEdit.frx":0000
   StartUpPosition =   1  'CenterOwner
End
Attribute VB_Name = "NameEdit"
Attribute VB_GlobalNameSpace = False
Attribute VB_Creatable = False
Attribute VB_PredeclaredId = True
Attribute VB_Exposed = False

Private attcount As Integer
Private thisNode As clsNode


Public Sub NameEdit_Initialize()
   Dim AttControl As Object
   For Each AttControl In NameEdit.Controls
       If Left(AttControl.Name, 4) = "attr" Then
          Me.Controls.Remove (AttControl.Name)
       End If
   Next
   attcount = 0
 Dim lastTop As Integer
 lastTop = Me.Controls(CopyName).Top
 SubmitButton.Top = lastTop + 30
 Me.Height = lastTop + 50
   
End Sub

Property Set eNode(ByRef activeNode As clsNode)
   Set thisNode = activeNode
End Property

Sub AddAttribute(AttName As String, AttValue As String)
  Dim cName As String
 Dim newControl As Control
 AttValue = URLDecode(AttValue)
 attcount = attcount + 1
 cName = "attr_" & attcount & "_name"
 Set newControl = Me.Controls.Add("Forms.TextBox.1", cName)
 Me.Controls(cName).Left = Attributes.Left
 Me.Controls(cName).Top = Attributes.Top + 20 * attcount
 Me.Controls(cName).value = AttName
 Me.Controls(cName).Width = Attributes.Width
  cName = "attr_" & attcount & "_value"
 Set newControl = Me.Controls.Add("Forms.TextBox.1", cName)
 Me.Controls(cName).Left = azNameValue.Left
 Dim lastTop As Integer
 lastTop = Attributes.Top + 20 * attcount
 Me.Controls(cName).Top = lastTop
 Me.Controls(cName).value = AttValue
 Me.Controls(cName).Width = azNameValue.Width
 SubmitButton.Top = lastTop + 30
 DataItems.Top = lastTop + 30
 DataItemsLabel.Top = lastTop + 30
 Me.Height = lastTop + 90
End Sub


Private Sub CommandButton1_Click()

End Sub

Private Sub SubmitButton_Click()
   Dim AttList As String
   
   AttList = """operation"":""edit"",""id"":""" & thisNode.AzquoName.Item("id") & """,""attributes"":{""DEFAULT_DISPLAY_NAME"":""" & azNameValue.value & """"
   Dim attNo As Integer
   For attNo = 1 To attcount
      Dim AttName As String
      AttName = Controls("attr_" & attNo & "_name").value
      If AttName > "" Then
         Dim AttValue As String
         AttValue = Controls("attr_" & attNo & "_value").value
         AttList = AttList + ",""" + AttName + """:""" + URLEncode(AttValue) + """"
      End If
    Next
     Dim azReply As String
    azReply = AzquoPost("Name", AttList & "}")
    If Left(azReply, 6) <> "error:" Then
        thisNode.caption = azNameValue.value
        azName.Item("name") = azNameValue.value
        Dim azAttributes As Object
        Set azAttributes = azName.Item("attributes")
        For Each azAttribute In azAttributes.Keys
           azAttributes.Remove (azAttribute)
        Next
        For attNo = 1 To attcount
           AttName = Controls("attr_" & attNo & "_name").value
           AttValue = Controls("attr_" & attNo & "_value").value
           If AttName > "" Then
              azAttributes.Add key:=AttName, Item:=AttValue
           End If
        Next
        
        NameEdit.Hide
    End If
          
         
End Sub

Public Function URLDecode(ByVal strEncodedURL As String) As String
   Dim str As String
   str = strEncodedURL
   If Len(str) > 0 Then
      str = Replace(str, "&amp", " & ")
      str = Replace(str, "&#03", Chr(39))
      str = Replace(str, "&quo", Chr(34))
      str = Replace(str, "+", " ")
      str = Replace(str, "%2A", "*")
      str = Replace(str, "%40", "@")
      str = Replace(str, "%2D", "-")
      str = Replace(str, "%5F", "_")
      str = Replace(str, "%2B", "+")
      str = Replace(str, "%2E", ".")
      str = Replace(str, "%2F", "/")
      str = Replace(str, "%5C%22", """") ' backslash needed for JSON
      str = Replace(str, "%28", "(")
      str = Replace(str, "%29", ")")
      
      URLDecode = str
  End If

End Function


Attribute VB_Name = "AzquoCommon"
#If Mac Then
  Private Declare Function popen Lib "libc.dylib" (ByVal command As String, ByVal mode As String) As Long
  Private Declare Function pclose Lib "libc.dylib" (ByVal file As Long) As Long
  Private Declare Function fread Lib "libc.dylib" (ByVal outStr As String, ByVal size As Long, ByVal Items As Long, ByVal stream As Long) As Long
  Private Declare Function feof Lib "libc.dylib" (ByVal file As Long) As Long
#End If
Global azConnectionId
Global az_Logon, az_Password
Global azResponse
Global azError
Global azNameChosen
Global azConnectionIDs As Object


Sub Auto_open()

    azConnectionId = ""
    azError = ""
    azVersion = "1.13"
End Sub




Function AzquoPost(strURL, strPostData)
  CheckConnectionId
  AzquoPost = AzquoPost1(strURL, strPostData)

End Function



Function logon()
   LogonForm.Show
   If azConnectionId = "aborted" Then
     End
   End If
End Function

Function RangeInThisSheet(RangeName As String)
   Dim RName, Current As Name
   For Each RName In ActiveWorkbook.Names
    Set Current = RName
    On Error GoTo zapcurrent
    If InStr(RName.Name, RangeName) = 1 And RName.RefersToRange.Worksheet.Name = ActiveSheet.Name Then
        Set RangeFound = Range(RName)
        If RangeFound.rows.Count = 1 And RangeFound.Columns.Count = 1 Then
           RangeInThisSheet = Range(RName).value
        Else
           RangeInThisSheet = RName
        End If
        Exit Function
     End If
   Next
   RangeInThisSheet = ""
   Exit Function
zapcurrent:
  Current.Delete
End Function




Sub CheckConnectionId()
Dim LogonOk As Boolean
Dim result As String
Dim Connection As String
LogonOk = False
 If RangeInThisSheet("az_DataRegion") > "" Then
   Connection = ActiveSheet.Name
 Else
   Connection = "AzquoMaintenance"
 End If
 azConnectionId = ""
 If azConnectionIDs Is Nothing Then
    Set azConnectionIDs = New Dictionary
 End If
 If azConnectionIDs.Exists(Connection) Then
     azConnectionId = azConnectionIDs.Item(Connection)
 End If
 While (azConnectionId = "")
     logon
     If azConnectionId > "" Then
         azConnectionIDs.Add key:=Connection, Item:=azConnectionId
         LogonOk = True
     Else
         End
     End If
  Wend
  
 While (Not LogonOk)
    Dim params As String
     params = "checkconnectionid=" & azConnectionId
    result = AzquoPost1("Login", params)
     LogonOk = True
  Wend
End Sub


Sub FillTheRange(DataIn, RegionName)

     Dim FillData, RowData As Variant
     Dim NoRows, LastRow, FirstInsert, LastInsert, FirstRemove, LastRemove, RowCount, RowNo, ColNo As Integer
     Dim FillRegion As Range
     Dim Formula As String
     
     
     If DataIn = "" Then
        Exit Sub
     End If
     If Right(DataIn, 1) = Strings.Chr(10) Then
        DataIn = Left(DataIn, Len(DataIn) - 1)
     End If
     FillData = Split(DataIn, Strings.Chr(10))
     
     NoRows = UBound(FillData) + 1
     
     Set FillRegion = Range(RegionName)
     LastRow = FillRegion.Row + FillRegion.rows.Count - 1
     If FillRegion.rows.Count < NoRows Then
         FirstInsert = LastRow
         LastInsert = FillRegion.Row + NoRows - 2
         rows(FirstInsert & ":" & LastInsert).Insert
         rows(LastRow - 1).Copy
         rows(LastRow & ":" & LastInsert).PasteSpecial
     End If
     RowCount = FillRegion.rows.Count
     If (NoRows > 1 And RowCount = 1) Then
       RowCount = 2
     End If
     If RowCount > NoRows Then
         LastRemove = FillRegion.Row + RowCount - 2
         FirstRemove = FillRegion.Row + NoRows - 1
         rows(FirstRemove & ":" & LastRemove).Delete
     End If
      RowData = Split(FillData(0), Strings.Chr(9))
     NoColumns = UBound(RowData) + 1
     ColumnsSent = NoColumns
     If FillRegion.Columns.Count < NoColumns Then
         FirstInsert = FillRegion.Column + FillRegion.Columns.Count - 1
         LastInsert = FillRegion.Column + NoColumns - 2
         Range(Columns(FirstInsert), Columns(LastInsert)).Insert
     End If
     ColCount = FillRegion.Columns.Count
     If NoColumns < 3 Then
        NoColumns = 3
     End If
     If ColCount > NoColumns Then
         LastRemove = FillRegion.Column + ColCount - 2
         FirstRemove = FillRegion.Column + NoColumns - 1
         Range(Columns(FirstRemove), Columns(LastRemove)).Delete
     End If
     For RowNo = 0 To NoRows - 1
       RowData = Split(FillData(RowNo), Strings.Chr(9))
       'If UBound(RowData) > 0 Then
          For ColNo = 0 To ColumnsSent - 1
            Set ThisCell = Cells(FillRegion.Row + RowNo, FillRegion.Column + ColNo)
            Formula = ThisCell.FormulaR1C1
            If Len(Formula) <= 1 Or Left(Formula, 1) <> "=" Then
               ThisCell.value = RowData(ColNo)
            End If
          Next
       'End If
     Next
            
    'Dim MyData As DataObject

     'Set MyData = New DataObject
     'MyData.SetText DataIn
     'MyData.PutInClipboard
     
     'FillRegion.Select
     'Application.DisplayAlerts = False
     'ActiveSheet.PasteSpecial Format:="Text"

     
     
End Sub




#If Mac Then

Function AzquoPost1(strURL, ByVal strPostData As String)
    If strURL = "Name" Then
       strPostData = "json={""connectionId"":""" & azConnectionId & """," & strPostData & "}"
    Else
       strPostData = "connectionid=" & azConnectionId & "&" & strPostData
    End If
     AzquoPost1 = HTTPPost("https://data.azquo.com:8443/api/" & strURL, strPostData)

     If Left(AzquoPost1, 6) = "error:" Then
        MsgBox (AzquoPost1)
        End
     End If

End Function


Function execShell(command As String, Optional ByRef exitCode As Long) As String
    Dim file As Long
    file = popen(command, "r")

    If file = 0 Then
        Exit Function
    End If

    While feof(file) = 0
        Dim chunk As String
        Dim read As Long
        chunk = Space(50)
        read = fread(chunk, 1, Len(chunk) - 1, file)
        If read > 0 Then
            chunk = Left$(chunk, read)
            execShell = execShell & chunk
        End If
    Wend

    exitCode = pclose(file)
End Function

Function HTTPPost(sUrl As String, sQuery As String) As String

    Dim sCmd As String
    Dim sResult As String
    Dim lExitCode As Long
  sQuery = Replace(sQuery, Chr(34), Chr(92) & Chr(34))

    sCmd = "curl --data """ & sQuery & """" & " " & sUrl
    sResult = execShell(sCmd, lExitCode)
'MsgBox (sCmd & " - " & sResult)
    ' ToDo check lExitCode

    HTTPPost = sResult

End Function


Function AzquoPostMulti(strURL As String, strPostData, strFileName As String)
     Dim sCmd As String
    Dim sResult As String
    Dim lExitCode As Long
    
strPostData = "connectionid=" & azConnectionId & "&" & strPostData

  ' ok one needs a seperate -f entry for EACH parameter, hence the replace below
  strPostData = Replace(strPostData, "&", """ -F """)
    sCmd = "curl -F """ & strPostData & """" & " -F ""source=@" & strFileName & """" & " -v https://data.azquo.com:8443/api/" & strURL
 '-F "source=@me.jpg" -F "message=Me" -v
    sResult = execShell(sCmd, lExitCode)
'MsgBox (sCmd & " - " & sResult)
    ' ToDo check lExitCode

    AzquoPostMulti = sResult
     If Left(AzquoPostMulti, 6) = "error:" Then
        MsgBox (AzquoPostMulti)
        End
     End If

End Function
#Else
Function AzquoPost1(strURL, strPostData)
    If strURL = "Name" Then
       strPostData = "json={""connectionId"":""" & azConnectionId & """," & strPostData & "}"
    Else
       strPostData = "connectionid=" & azConnectionId & "&" & strPostData
    End If
    Dim pXmlHttp As Object
    Set pXmlHttp = CreateObject("MSXML2.XMLHTTP")
      pXmlHttp.Open "POST", "https://data.azquo.com:8443/api/" & strURL, False
    pXmlHttp.SetRequestHeader "Content-Type", "application/x-www-form-urlencoded"
    pXmlHttp.Send (strPostData)


     AzquoPost1 = pXmlHttp.responseText
     If Left(AzquoPost1, 6) = "error:" Then
        MsgBox (AzquoPost1)
        End
     End If

End Function


Function AzquoPostMulti(strURL As String, strPostData, strFileName As String)
    Const MULTIPART_BOUNDARY = "9876543210----------0123456789"

    Dim strResponse, xmlhttp, xmlDom, returnVal
    Dim strData

    strData = ""

    strData = strData & "--" & MULTIPART_BOUNDARY & vbCrLf
    strData = strData & "Content-Disposition: form-data; name=""parameters""" & vbCrLf & vbCrLf
    strData = strData & strPostData & "&connectionid=" & azConnectionId
    strData = strData & vbCrLf

     strData = strData & "--" & MULTIPART_BOUNDARY & vbCrLf
    strData = strData & "Content-Disposition: form-data; name=""uploadfile""" & vbCrLf
     strData = strData & "Content-Transfer-Encoding: binary" & vbCrLf & vbCrLf
    If Right(strFileName, 4) = ".xls" Or Right(strFileName, 4) = ".zip" Then
       strData = strData & EncodeBase64(ReadFile(strFileName))
    Else
       strData = strData & ReadFile(strFileName)
    End If
    strData = strData & vbCrLf
    
    
   strData = strData & "--" & MULTIPART_BOUNDARY & "--"

    Set xmlhttp = CreateObject("Microsoft.XMLHTTP")
    xmlhttp.Open "POST", "https://data.azquo.com:8443/api/Import", False

    xmlhttp.SetRequestHeader "Accept", "application/x-www-form-urlencoded,text/xml,application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5"
    xmlhttp.SetRequestHeader "Accept-Language", "en-us,en;q=0.5"
    xmlhttp.SetRequestHeader "Accept-Charset", "ISO-8859-1,utf-8;q=0.7,*;q=0.7"
    xmlhttp.SetRequestHeader "Content-Type", "multipart/form-data; boundary=" & MULTIPART_BOUNDARY
    xmlhttp.SetRequestHeader "Content-Length", Len(strData) + 2
    xmlhttp.Send strData

    AzquoPostMulti = xmlhttp.responseText
     If Left(AzquoPostMulti, 6) = "error:" Then
        MsgBox (AzquoPostMulti)
        End
     End If

End Function
#End If

Public Function URLEncode(ByVal StringVal As String) As String

  Dim StringLen As Long: StringLen = Len(StringVal)

  If StringLen > 0 Then
    ReDim result(StringLen) As String
    Dim i As Long, CharCode As Integer
    Dim Char As String, Space As String

    Space = "%20"

    For i = 1 To StringLen
      Char = Mid$(StringVal, i, 1)
      CharCode = Asc(Char)
      Select Case CharCode
        Case 97 To 122, 65 To 90, 48 To 57, 45, 46, 95, 126
          result(i) = Char
        Case 32
          result(i) = Space
        Case 0 To 15
          result(i) = "%0" & Hex(CharCode)
        Case Else
          result(i) = "%" & Hex(CharCode)
      End Select
    Next i
    URLEncode = Join(result, "")
  End If
End Function

